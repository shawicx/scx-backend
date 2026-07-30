package com.scx.backend.modules.mail.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/** 发送验证码邮件请求 */
@Schema(description = "发送验证码邮件请求")
data class SendVerificationCodeDto(
    @Schema(description = "收件邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
)

/** 发送欢迎邮件请求 */
@Schema(description = "发送欢迎邮件请求")
data class SendWelcomeEmailDto(
    @Schema(description = "收件邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @Schema(description = "用户名称（用于模板渲染）", required = true)
    @field:NotBlank(message = "用户名不能为空")
    val username: String,
)

/** 发送密码重置邮件请求 */
@Schema(description = "发送密码重置邮件请求")
data class SendPasswordResetDto(
    @Schema(description = "收件邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @Schema(description = "密码重置令牌", required = true)
    @field:NotBlank(message = "重置令牌不能为空")
    val resetToken: String,

    @Schema(description = "密码重置链接", required = true)
    @field:NotBlank(message = "重置链接不能为空")
    val resetUrl: String,
)

/** 发送自定义 HTML 邮件请求 */
@Schema(description = "发送自定义 HTML 邮件请求")
data class SendHtmlEmailDto(
    @Schema(description = "收件邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @Schema(description = "邮件主题", required = true)
    @field:NotBlank(message = "邮件主题不能为空")
    val subject: String,

    @Schema(description = "HTML 邮件内容", required = true)
    @field:NotBlank(message = "HTML内容不能为空")
    val html: String,
)
