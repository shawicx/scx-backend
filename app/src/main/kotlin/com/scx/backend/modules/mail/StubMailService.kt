package com.scx.backend.modules.mail

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 邮件服务 Stub 实现（测试/无 SMTP 环境）
 *
 * 生成验证码但不发送真实邮件。当 mail.enabled=false 时激活（默认 true 用 SmtpMailService）。
 */
@Service
@ConditionalOnProperty(name = ["mail.enabled"], havingValue = "false")
class StubMailService : MailService {

    override fun sendVerificationCode(to: String): MailService.SendResult {
        val code = Random.nextInt(100000, 1000000).toString()
        return MailService.SendResult(success = true, message = "验证码邮件发送成功（stub）", code = code)
    }

    override fun sendWelcomeEmail(to: String, username: String) =
        MailService.SendResult(true, "欢迎邮件发送成功（stub）")

    override fun sendPasswordResetEmail(to: String, resetToken: String, resetUrl: String) =
        MailService.SendResult(true, "密码重置邮件发送成功（stub）")

    override fun sendHtmlMail(to: String, subject: String, html: String) =
        MailService.SendResult(true, "HTML邮件发送成功（stub）")

    override fun testConnection() = MailService.SendResult(true, "邮件配置测试成功（stub）")
}
