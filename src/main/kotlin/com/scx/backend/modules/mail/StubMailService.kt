package com.scx.backend.modules.mail

import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 邮件服务 Stub 实现
 *
 * 返回固定/随机验证码，不发真实邮件。
 * Step 9 实现真实 SMTP + Thymeleaf 模板后替换此实现。
 */
@Service
@org.springframework.context.annotation.Primary // 优先于将来可能的其它实现
class StubMailService : MailService {

    override fun sendVerificationCode(email: String): MailService.SendResult {
        // 生成 6 位数字验证码
        val code = Random.nextInt(100000, 1000000).toString()
        return MailService.SendResult(success = true, code = code)
    }

    override fun sendWelcomeEmail(email: String, name: String): Boolean = true

    override fun sendPasswordResetEmail(email: String, resetToken: String): Boolean = true
}
