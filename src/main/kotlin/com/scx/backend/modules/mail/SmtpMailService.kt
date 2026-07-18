package com.scx.backend.modules.mail

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * SMTP 邮件服务实现
 * 对标 scx-service: src/modules/mail/mail.service.ts
 *
 * 用 JavaMailSender 发送，Thymeleaf 渲染模板。
 * 带超时控制（对标源 sendMailWithTimeout），错误分类（对标源 parseMailError）。
 */
@Service
@Primary
@ConditionalOnProperty(name = ["mail.enabled"], havingValue = "true", matchIfMissing = true)
class SmtpMailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: SpringTemplateEngine,
    @Value("\${spring.mail.username:noreply@scx.dev}") private val fromEmail: String,
    @Value("\${app.name:SCX Service}") private val appName: String,
    @Value("\${mail.timeout-ms:30000}") private val timeoutMs: Long,
) : MailService {

    private val logger = LoggerFactory.getLogger(SmtpMailService::class.java)

    override fun sendVerificationCode(to: String): MailService.SendResult {
        val code = (100000 + (Math.random() * 900000).toInt()).toString()
        val context = Context().apply {
            setVariable("code", code)
            setVariable("appName", appName)
            setVariable("year", java.time.Year.now().value)
        }
        val html = renderTemplate("mail/verification-code", context)
        return sendTemplateMail(to, "【$appName】您的验证码", html, code)
    }

    override fun sendWelcomeEmail(to: String, username: String): MailService.SendResult {
        val context = Context().apply {
            setVariable("username", username)
            setVariable("appName", appName)
            setVariable("year", java.time.Year.now().value)
        }
        val html = renderTemplate("mail/welcome", context)
        return sendTemplateMail(to, "欢迎加入 $appName！", html)
    }

    override fun sendPasswordResetEmail(to: String, resetToken: String, resetUrl: String): MailService.SendResult {
        val context = Context().apply {
            setVariable("resetToken", resetToken)
            setVariable("resetUrl", resetUrl)
            setVariable("appName", appName)
            setVariable("year", java.time.Year.now().value)
        }
        val html = renderTemplate("mail/password-reset", context)
        return sendTemplateMail(to, "【$appName】密码重置请求", html)
    }

    override fun sendHtmlMail(to: String, subject: String, html: String): MailService.SendResult =
        sendTemplateMail(to, subject, html)

    override fun testConnection(): MailService.SendResult {
        return try {
            val html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                  <h2 style="color: #333;">邮件配置测试成功</h2>
                  <p>如果您收到这封邮件，说明邮件配置正常工作。</p>
                </div>
            """.trimIndent()
            sendWithTimeout(buildMimeMessage(fromEmail, "邮件配置测试", html))
            logger.info("邮件配置测试成功")
            MailService.SendResult(true, "邮件配置测试成功")
        } catch (e: Exception) {
            logger.error("邮件配置测试失败: {}", e.message)
            MailService.SendResult(false, "邮件配置测试失败: ${e.message}", error = e.message)
        }
    }

    // ============ 内部方法 ============

    private fun renderTemplate(template: String, context: Context): String =
        templateEngine.process(template, context)

    /** 发送模板邮件（带验证码时附加 code 到结果） */
    private fun sendTemplateMail(to: String, subject: String, html: String, code: String? = null): MailService.SendResult {
        return try {
            sendWithTimeout(buildMimeMessage(to, subject, html))
            logger.info("邮件发送成功: {} - {}", to, subject)
            MailService.SendResult(true, "邮件发送成功", code = code)
        } catch (e: Exception) {
            val error = parseError(e)
            logger.error("邮件发送失败: {} | type={} msg={}", to, error.first, error.second, e)
            MailService.SendResult(false, "邮件发送失败", code = code, error = error.second)
        }
    }

    private fun buildMimeMessage(to: String, subject: String, html: String): MimeMessage {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(fromEmail)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true) // true = HTML
        return message
    }

    /**
     * 带超时的发送（对标源 sendMailWithTimeout）
     * CompletableFuture.orTimeout 在超时后抛 TimeoutException
     */
    private fun sendWithTimeout(message: MimeMessage) {
        val future = CompletableFuture.runAsync { mailSender.send(message) }
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            throw RuntimeException("邮件发送超时 (${timeoutMs}ms)")
        }
    }

    /** 错误分类（对标源 parseMailError） */
    private fun parseError(e: Throwable): Pair<String, String> {
        val msg = e.message ?: "未知错误"
        val type = when {
            msg.contains("超时") || msg.contains("timeout", ignoreCase = true) -> "TIMEOUT"
            msg.contains("Authentication", ignoreCase = true) || msg.contains("auth", ignoreCase = true) -> "AUTHENTICATION"
            msg.contains("ENOTFOUND", ignoreCase = true) || msg.contains("ECONNREFUSED", ignoreCase = true) -> "NETWORK"
            msg.contains("template", ignoreCase = true) -> "TEMPLATE"
            msg.contains("Invalid", ignoreCase = true) || msg.contains("validation", ignoreCase = true) -> "VALIDATION"
            else -> "UNKNOWN"
        }
        return type to msg
    }
}
