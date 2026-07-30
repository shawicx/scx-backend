package com.scx.backend.modules.mail

import com.scx.backend.common.decorator.Public
import com.scx.backend.common.exception.SystemException
import com.scx.backend.modules.mail.dto.SendHtmlEmailDto
import com.scx.backend.modules.mail.dto.SendPasswordResetDto
import com.scx.backend.modules.mail.dto.SendVerificationCodeDto
import com.scx.backend.modules.mail.dto.SendWelcomeEmailDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 邮件控制器
 *
 * 路由前缀 /api/mail（由 context-path=/api 提供）
 * 所有接口均为 @Public 公开调用
 */
@Tag(name = "邮件服务", description = "验证码、欢迎、密码重置及自定义 HTML 邮件的发送")
@RestController
@RequestMapping("/mail", produces = [MediaType.APPLICATION_JSON_VALUE])
class MailController(
    private val mailService: MailService,
) {
    @Public
    @Operation(summary = "发送验证码邮件", description = "向指定邮箱发送 6 位数字验证码")
    @PostMapping("/send-verification-code")
    fun sendVerificationCode(@Valid @RequestBody dto: SendVerificationCodeDto) =
        mailService.sendVerificationCode(dto.email)

    @Public
    @Operation(summary = "发送欢迎邮件", description = "向指定邮箱发送欢迎邮件，使用欢迎模板")
    @PostMapping("/send-welcome-email")
    fun sendWelcomeEmail(@Valid @RequestBody dto: SendWelcomeEmailDto) =
        mailService.sendWelcomeEmail(dto.email, dto.username)

    @Public
    @Operation(summary = "发送密码重置邮件", description = "向指定邮箱发送含重置链接的密码重置邮件")
    @PostMapping("/send-password-reset")
    fun sendPasswordResetEmail(@Valid @RequestBody dto: SendPasswordResetDto) {
        val result = mailService.sendPasswordResetEmail(dto.email, dto.resetToken, dto.resetUrl)
        if (!result.success) {
            throw SystemException.operationFailed(result.error ?: result.message)
        }
    }

    @Public
    @Operation(summary = "发送自定义 HTML 邮件", description = "向指定邮箱发送自定义主题与 HTML 内容的邮件")
    @PostMapping("/send-html-email")
    fun sendHtmlEmail(@Valid @RequestBody dto: SendHtmlEmailDto) {
        val result = mailService.sendHtmlMail(dto.email, dto.subject, dto.html)
        if (!result.success) {
            throw SystemException.operationFailed(result.error ?: result.message)
        }
    }
}
