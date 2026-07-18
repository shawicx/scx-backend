package com.scx.backend.modules.mail.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/** 发送验证码（对标 SendVerificationCodeDto） */
data class SendVerificationCodeDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
)

/** 发送欢迎邮件（对标 SendWelcomeEmailDto） */
data class SendWelcomeEmailDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
    @field:NotBlank(message = "用户名不能为空")
    val username: String,
)

/** 发送密码重置（对标 SendPasswordResetDto） */
data class SendPasswordResetDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
    @field:NotBlank(message = "重置令牌不能为空")
    val resetToken: String,
    @field:NotBlank(message = "重置链接不能为空")
    val resetUrl: String,
)

/** 发送 HTML 邮件（对标 SendHtmlEmailDto） */
data class SendHtmlEmailDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
    @field:NotBlank(message = "邮件主题不能为空")
    val subject: String,
    @field:NotBlank(message = "HTML内容不能为空")
    val html: String,
)
