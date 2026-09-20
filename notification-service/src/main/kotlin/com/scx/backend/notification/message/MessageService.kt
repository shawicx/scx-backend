package com.scx.backend.notification.message

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.notification.entity.Notification
import com.scx.backend.notification.message.dto.MessageListResponseDto
import com.scx.backend.notification.message.dto.NotificationAdminDto
import com.scx.backend.notification.message.dto.NotificationDetailDto
import com.scx.backend.notification.message.dto.SendResultDto
import com.scx.backend.notification.message.dto.SendMessageDto
import com.scx.backend.notification.repository.ChannelRepository
import com.scx.backend.notification.repository.NotificationRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @description 消息发送服务：校验 → 收件人解析 → 主记录落库 → 按渠道投递类型分发
 *
 * INBOX/ANNOUNCEMENT 同事务批量扇出（500/批）；EMAIL 异步逐封发送。
 */
@Service
class MessageService(
    private val channelRepository: ChannelRepository,
    private val notificationRepository: NotificationRepository,
    private val recipientResolver: RecipientResolver,
    private val emailDeliveryService: EmailDeliveryService,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(MessageService::class.java)

    /**
     * @description 发送消息（发消息必须有渠道；目标解析为空则拒绝）
     * @param dto 发送请求
     * @param senderId 发送者用户 ID（管理员）
     * @returns SendResultDto 消息 ID 与实际投递人数
     */
    @Transactional
    fun send(dto: SendMessageDto, senderId: String): SendResultDto {
        val channel = channelRepository.findById(dto.channelId).orElseThrow {
            SystemException.dataNotFound("Channel with ID '${dto.channelId}' not found")
        }
        if (!channel.isActive) {
            throw SystemException.businessRuleViolation("渠道已停用，不可用于发送")
        }
        if (dto.targetType != "ALL" && dto.targetIds.isNullOrEmpty()) {
            throw SystemException.invalidParameter("targetType=${dto.targetType} 必须指定非空 targetIds")
        }

        val recipients = when (dto.targetType) {
            "ALL" -> recipientResolver.resolveAll()
            "ROLES" -> recipientResolver.resolveByRoleIds(dto.targetIds!!.distinct())
            else -> recipientResolver.resolveByUserIds(dto.targetIds!!.distinct())
        }
        if (recipients.isEmpty()) {
            throw SystemException.businessRuleViolation("目标收件人为空（可能均已被禁用或删除）")
        }

        // saveAndFlush：确保主记录先落库（后续原生 JDBC 扇出插入子行，需满足 FK 顺序）
        val notification = notificationRepository.saveAndFlush(
            Notification(
                id = IdGenerator.nextId(),
                channel = channel,
                title = dto.title,
                content = dto.content,
                link = dto.link,
                level = dto.level ?: "INFO",
                pinned = dto.pinned ?: false,
                targetType = dto.targetType,
                targetIds = if (dto.targetType == "ALL") null
                    else objectMapper.writeValueAsString(dto.targetIds!!.distinct()),
                recipientCount = recipients.size,
                senderId = senderId,
            ),
        )

        when (channel.type) {
            "EMAIL" -> emailDeliveryService.sendBulk(
                recipientResolver.resolveEmails(recipients).values.toList(),
                dto.title,
                dto.content,
            )
            else -> insertUserNotifications(notification.id, recipients)
        }

        logger.info("Notification sent: {} via channel {} to {} recipients", notification.id, channel.code, recipients.size)
        return SendResultDto(notificationId = notification.id, recipientCount = recipients.size)
    }

    /**
     * @description 管理端消息分页（按渠道 / 目标类型筛选）
     */
    @Transactional(readOnly = true)
    fun findAll(page: Int = 1, limit: Int = 10, channelId: String?, targetType: String?): MessageListResponseDto {
        val spec = Specification<Notification> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (!channelId.isNullOrBlank()) predicates += cb.equal(root.get<com.scx.backend.notification.entity.Channel>("channel").get<String>("id"), channelId)
            if (!targetType.isNullOrBlank()) predicates += cb.equal(root.get<String>("targetType"), targetType)
            cb.and(*predicates.toTypedArray())
        }
        val pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = notificationRepository.findAll(spec, pageable)
        return MessageListResponseDto(
            list = result.content.map { NotificationAdminDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 消息详情（含 targetIds 解析后的审计列表）
     * @param id 消息 ID
     * @returns NotificationDetailDto 详情
     */
    @Transactional(readOnly = true)
    fun findById(id: String): NotificationDetailDto {
        val n = notificationRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Notification with ID '$id' not found")
        }
        val targetIdList = n.targetIds?.let {
            objectMapper.readValue(it, Array<String>::class.java).toList()
        }
        return NotificationDetailDto(notification = NotificationAdminDto.from(n), targetIdList = targetIdList)
    }

    /**
     * @description 批量插入用户投递行（写扩散，500/批，与发送同事务）
     * @param notificationId 消息 ID
     * @param userIds 收件用户 ID 列表
     */
    private fun insertUserNotifications(notificationId: String, userIds: List<String>) {
        val sql = """INSERT INTO user_notifications (id, "notificationId", "userId") VALUES (:id, :notificationId, :userId)"""
        userIds.chunked(500).forEach { chunk ->
            val params = chunk.map {
                MapSqlParameterSource()
                    .addValue("id", IdGenerator.nextId())
                    .addValue("notificationId", notificationId)
                    .addValue("userId", it)
            }
            jdbcTemplate.batchUpdate(sql, params.toTypedArray())
        }
    }
}
