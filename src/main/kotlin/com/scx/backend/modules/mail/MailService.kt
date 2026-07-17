package com.scx.backend.modules.mail

/**
 * 邮件服务接口
 * 对标 scx-service: src/modules/mail/mail.service.ts
 *
 * Step 9 会替换为真实 SMTP 实现（Thymeleaf 模板）。
 * 当前为 stub，返回固定验证码，使 UserService 能跑通完整流程。
 */
interface MailService {

    /**
     * 发送验证码邮件（内部生成 6 位）
     * @return 发送结果（含验证码），success=false 表示发送失败
     */
    fun sendVerificationCode(email: String): SendResult

    /**
     * 发送欢迎邮件
     */
    fun sendWelcomeEmail(email: String, name: String): Boolean

    /**
     * 发送密码重置邮件
     */
    fun sendPasswordResetEmail(email: String, resetToken: String): Boolean

    /** 发送结果 */
    data class SendResult(val success: Boolean, val code: String? = null)
}
