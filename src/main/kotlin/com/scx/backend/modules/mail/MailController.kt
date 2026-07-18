package com.scx.backend.modules.mail

import com.scx.backend.common.decorator.Public
import com.scx.backend.common.exception.SystemException
import com.scx.backend.modules.mail.dto.SendHtmlEmailDto
import com.scx.backend.modules.mail.dto.SendPasswordResetDto
import com.scx.backend.modules.mail.dto.SendVerificationCodeDto
import com.scx.backend.modules.mail.dto.SendWelcomeEmailDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 邮件控制器
 * 对标 scx-service: src/modules/mail/mail.controller.ts
 *
 * 路由前缀 /api/mail（由 context-path=/api 提供）
 * 所有接口 @Public（对标源）
 */
@RestController
@RequestMapping("/mail")
class MailController(
    private val mailService: MailService,
) {
    @Public
    @PostMapping("/send-verification-code")
    fun sendVerificationCode(@Valid @RequestBody dto: SendVerificationCodeDto) =
        mailService.sendVerificationCode(dto.email)

    @Public
    @PostMapping("/send-welcome-email")
    fun sendWelcomeEmail(@Valid @RequestBody dto: SendWelcomeEmailDto) =
        mailService.sendWelcomeEmail(dto.email, dto.username)

    @Public
    @PostMapping("/send-password-reset")
    fun sendPasswordResetEmail(@Valid @RequestBody dto: SendPasswordResetDto) {
        val result = mailService.sendPasswordResetEmail(dto.email, dto.resetToken, dto.resetUrl)
        if (!result.success) {
            throw SystemException.operationFailed(result.error ?: result.message)
        }
    }

    @Public
    @PostMapping("/send-html-email")
    fun sendHtmlEmail(@Valid @RequestBody dto: SendHtmlEmailDto) {
        val result = mailService.sendHtmlMail(dto.email, dto.subject, dto.html)
        if (!result.success) {
            throw SystemException.operationFailed(result.error ?: result.message)
        }
    }
}
