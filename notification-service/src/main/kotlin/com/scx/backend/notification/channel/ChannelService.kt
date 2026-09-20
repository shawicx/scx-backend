package com.scx.backend.notification.channel

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.notification.channel.dto.ChannelListResponseDto
import com.scx.backend.notification.channel.dto.ChannelResponseDto
import com.scx.backend.notification.channel.dto.CreateChannelDto
import com.scx.backend.notification.channel.dto.UpdateChannelDto
import com.scx.backend.notification.entity.Channel
import com.scx.backend.notification.repository.ChannelRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @description 渠道管理服务：CRUD 与系统渠道保护规则
 */
@Service
class ChannelService(
    private val channelRepository: ChannelRepository,
) {
    private val logger = LoggerFactory.getLogger(ChannelService::class.java)

    /**
     * @description 创建渠道（code 唯一；isSystem 只能由种子产生，恒为 false）
     * @param dto 创建请求
     * @returns ChannelResponseDto 创建结果
     */
    @Transactional
    fun create(dto: CreateChannelDto): ChannelResponseDto {
        if (channelRepository.existsByCode(dto.code)) {
            throw SystemException.resourceExists("Channel with code '${dto.code}' already exists")
        }
        val saved = channelRepository.save(
            Channel(
                id = IdGenerator.nextId(),
                code = dto.code,
                name = dto.name,
                type = dto.type,
                description = dto.description,
                isSystem = false,
                isActive = true,
            ),
        )
        logger.info("Channel created: {} ({})", saved.name, saved.code)
        return ChannelResponseDto.from(saved)
    }

    /**
     * @description 渠道分页列表（可按投递类型与关键字过滤）
     */
    fun findAll(page: Int = 1, limit: Int = 10, type: String?, keyword: String?): ChannelListResponseDto {
        val spec = Specification<Channel> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (!type.isNullOrBlank()) predicates += cb.equal(root.get<String>("type"), type)
            if (!keyword.isNullOrBlank()) {
                val like = "%${keyword.lowercase()}%"
                predicates += cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("name")), like),
                )
            }
            cb.and(*predicates.toTypedArray())
        }
        val pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = channelRepository.findAll(spec, pageable)
        return ChannelListResponseDto(
            list = result.content.map { ChannelResponseDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 全量启用渠道（发消息下拉一次加载）
     * @returns List<ChannelResponseDto> 启用中的渠道，创建时间倒序
     */
    fun getAllActive(): List<ChannelResponseDto> =
        channelRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .filter { it.isActive }
            .map { ChannelResponseDto.from(it) }

    /**
     * @description 更新渠道（仅 name/description/isActive；code 与 type 不可改）
     */
    @Transactional
    fun update(dto: UpdateChannelDto): ChannelResponseDto {
        val channel = channelRepository.findById(dto.id).orElseThrow {
            SystemException.dataNotFound("Channel with ID '${dto.id}' not found")
        }
        dto.name?.let { channel.name = it }
        dto.description?.let { channel.description = it }
        dto.isActive?.let { channel.isActive = it }
        val updated = channelRepository.save(channel)
        logger.info("Channel updated: {} ({})", updated.name, updated.code)
        return ChannelResponseDto.from(updated)
    }

    /**
     * @description 删除渠道（系统渠道不可删；历史消息随外键级联删除）
     */
    @Transactional
    fun delete(id: String) {
        val channel = channelRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Channel with ID '$id' not found")
        }
        if (channel.isSystem) {
            throw SystemException.businessRuleViolation("系统渠道不可删除")
        }
        channelRepository.delete(channel)
        logger.info("Channel deleted: {} ({})", channel.name, channel.code)
    }
}
