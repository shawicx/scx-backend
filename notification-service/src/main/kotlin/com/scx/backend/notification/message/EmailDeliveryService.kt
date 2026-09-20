package com.scx.backend.notification.message

import com.scx.backend.notification.mail.MailService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * @description EMAIL 渠道异步投递服务
 *
 * 逐封发送、失败记日志不阻断；邮件失败不回滚消息主记录
 * （站内投递与主记录同事务，邮件投递为尽力而为）。
 */
@Service
class EmailDeliveryService(
    private val mailService: MailService,
) {
    private val logger = LoggerFactory.getLogger(EmailDeliveryService::class.java)

    /**
     * @description 批量发送通知邮件（@Async 异步执行）
     * @param emails 收件邮箱列表
     * @param subject 邮件主题（消息标题）
     * @param html 邮件 HTML 正文（消息内容）
     */
    @Async
    fun sendBulk(emails: List<String>, subject: String, html: String) {
        emails.forEach { to ->
            val result = mailService.sendHtmlMail(to, subject, html)
            if (!result.success) {
                logger.error("通知邮件投递失败: {} | {}", to, result.error)
            }
        }
    }
}
