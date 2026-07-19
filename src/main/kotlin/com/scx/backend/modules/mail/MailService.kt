package com.scx.backend.modules.mail

/**
 * 邮件服务接口
 *
 * 实现由 mail.enabled 控制：
 *  - false：使用 StubMailService（不真实发信）
 *  - true：使用 SmtpMailService（Thymeleaf 模板 + JavaMailSender）
 */
interface MailService {

    /**
     * 发送验证码邮件（内部生成 6 位数字）
     * @return 发送结果（成功时含验证码 code）
     */
    fun sendVerificationCode(to: String): SendResult

    /**
     * 发送欢迎邮件
     * @param to 收件人
     * @param username 用户名
     */
    fun sendWelcomeEmail(to: String, username: String): SendResult

    /**
     * 发送密码重置邮件
     * @param to 收件人
     * @param resetToken 重置令牌
     * @param resetUrl 重置链接
     */
    fun sendPasswordResetEmail(to: String, resetToken: String, resetUrl: String): SendResult

    /**
     * 发送自定义 HTML 邮件
     */
    fun sendHtmlMail(to: String, subject: String, html: String): SendResult

    /**
     * 测试邮件配置（给自己发一封测试邮件）
     */
    fun testConnection(): SendResult

    /** 发送结果 */
    data class SendResult(
        val success: Boolean,
        val message: String,
        val code: String? = null,
        val error: String? = null,
    )
}
