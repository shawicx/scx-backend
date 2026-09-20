package com.scx.backend.notification.mail

import com.scx.backend.notification.repository.NotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * @description 事务邮件落库测试：发送成功后记录 EMAIL_CODE 渠道消息
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=true",
        "spring.datasource.url=jdbc:h2:mem:notifyMailRec;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class MailRecordTest(
    @Autowired private val mailService: MailService,
    @Autowired private val notificationRepository: NotificationRepository,
) {
    @Test
    fun `verification code send records a DIRECT notification under EMAIL_CODE channel`() {
        val before = notificationRepository.count()
        val result = mailService.sendVerificationCode("someone@test.dev")
        assertTrue(result.success)

        val after = notificationRepository.count()
        assertEquals(before + 1, after)

        val recorded = notificationRepository.findAll().last()
        assertEquals("DIRECT", recorded.targetType)
        assertEquals("EMAIL_CODE", recorded.channel.code)
        assertEquals("someone@test.dev", recorded.recipientEmail)
    }

    @Test
    fun `welcome and password reset and html mail also record`() {
        mailService.sendWelcomeEmail("w@test.dev", "张三")
        mailService.sendPasswordResetEmail("p@test.dev", "token", "http://reset")
        mailService.sendHtmlMail("h@test.dev", "自定义", "<p>hi</p>")
        assertEquals(3, notificationRepository.count())
    }
}
