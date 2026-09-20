package com.scx.backend.notification.mail

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.notification.entity.Notification
import com.scx.backend.notification.repository.ChannelRepository
import com.scx.backend.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * @description 事务邮件落库记录器
 *
 * Smtp/Stub 发送成功后把邮件记录为 EMAIL_CODE 渠道的 DIRECT 消息（统计用）。
 * 落库失败仅记日志——发信永远优先于记录；EMAIL_CODE 渠道缺失（种子未跑）时静默跳过。
 */
@Component
class MailSendRecorder(
    private val channelRepository: ChannelRepository,
    private val notificationRepository: NotificationRepository,
) {
    private val logger = LoggerFactory.getLogger(MailSendRecorder::class.java)

    companion object {
        const val CHANNEL_CODE = "EMAIL_CODE"
    }

    /**
     * @description 记录一次事务邮件发送（不写 user_notifications，不进收件箱）
     * @param to 收件邮箱
     * @param title 邮件主题（同时作为 content 存档）
     */
    fun record(to: String, title: String) {
        try {
            val channel = channelRepository.findByCode(CHANNEL_CODE) ?: return
            notificationRepository.save(
                Notification(
                    id = IdGenerator.nextId(),
                    channel = channel,
                    title = title,
                    content = title,
                    targetType = "DIRECT",
                    recipientEmail = to,
                    recipientCount = 1,
                ),
            )
        } catch (e: Exception) {
            logger.warn("事务邮件落库失败（不影响发送）: {}", e.message)
        }
    }
}
