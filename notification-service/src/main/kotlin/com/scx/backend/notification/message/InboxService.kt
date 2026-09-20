package com.scx.backend.notification.message

import com.scx.backend.common.exception.SystemException
import com.scx.backend.notification.entity.UserNotification
import com.scx.backend.notification.message.dto.InboxItemDto
import com.scx.backend.notification.message.dto.InboxListResponseDto
import com.scx.backend.notification.message.dto.UnreadCountDto
import com.scx.backend.notification.repository.UserNotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 用户端收件箱服务：收件箱 / 公告列表 / 未读数 / 已读标记
 *
 * INBOX 与 ANNOUNCEMENT 同为写扩散扇出行，按渠道 type 分流展示。
 */
@Service
class InboxService(
    private val userNotificationRepository: UserNotificationRepository,
) {
    /**
     * @description 我的收件箱（INBOX 渠道，未读在前、时间倒序）
     */
    @Transactional(readOnly = true)
    fun inbox(userId: String, page: Int = 1, limit: Int = 10): InboxListResponseDto =
        pageByChannelType(userId, "INBOX", page, limit)

    /**
     * @description 公告列表（ANNOUNCEMENT 渠道，置顶在前、时间倒序，含已读状态）
     */
    @Transactional(readOnly = true)
    fun announcements(userId: String, page: Int = 1, limit: Int = 10): InboxListResponseDto {
        val pageable = PageRequest.of(page - 1, limit)
        val result = userNotificationRepository.findAnnouncementPageByUserId(userId, pageable)
        return InboxListResponseDto(
            list = result.content.map { InboxItemDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 未读数分桶统计（收件箱 + 公告）
     */
    @Transactional(readOnly = true)
    fun unreadCount(userId: String): UnreadCountDto = UnreadCountDto(
        inbox = userNotificationRepository.countUnreadByUserIdAndChannelType(userId, "INBOX"),
        announcement = userNotificationRepository.countUnreadByUserIdAndChannelType(userId, "ANNOUNCEMENT"),
    )

    /**
     * @description 标记单条已读（非本人投递按 9002 处理，不泄露存在性；重复标记幂等）
     */
    @Transactional
    fun markRead(userId: String, notificationId: String) {
        val un = userNotificationRepository.findByUserIdAndNotificationId(userId, notificationId)
            ?: throw SystemException.dataNotFound("Notification not found")
        if (un.readAt == null) {
            un.readAt = LocalDateTime.now()
            userNotificationRepository.save(un)
        }
    }

    /**
     * @description 全部标记已读（可按渠道缩小范围）
     * @returns Int 标记行数
     */
    @Transactional
    fun markAllRead(userId: String, channelIds: List<String>?): Int =
        if (channelIds.isNullOrEmpty()) {
            userNotificationRepository.markAllRead(userId)
        } else {
            userNotificationRepository.markAllReadByChannels(userId, channelIds)
        }

    /**
     * @description 按渠道投递类型分页查询用户消息
     */
    private fun pageByChannelType(userId: String, channelType: String, page: Int, limit: Int): InboxListResponseDto {
        val pageable = PageRequest.of(page - 1, limit)
        val result = userNotificationRepository.findPageByUserIdAndChannelType(userId, channelType, pageable)
        return InboxListResponseDto(
            list = result.content.map { InboxItemDto.from(it) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }
}
