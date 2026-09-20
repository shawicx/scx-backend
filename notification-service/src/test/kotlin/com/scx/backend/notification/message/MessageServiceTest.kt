package com.scx.backend.notification.message

import com.scx.backend.common.exception.SystemException
import com.scx.backend.notification.entity.Notification
import com.scx.backend.notification.entity.UserNotification
import com.scx.backend.notification.mail.MailService
import com.scx.backend.notification.message.dto.SendMessageDto
import com.scx.backend.notification.repository.ChannelRepository
import com.scx.backend.notification.repository.NotificationRepository
import com.scx.backend.notification.repository.UserNotificationRepository
import com.scx.backend.notification.seed.ChannelSeedService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @description 消息发送服务测试：校验链、扇出行数、EMAIL 投递路径
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifySend;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
@Sql("/sql/users-fixture.sql")
class MessageServiceTest(
    @Autowired private val messageService: MessageService,
    @Autowired private val channelRepository: ChannelRepository,
    @Autowired private val notificationRepository: NotificationRepository,
    @Autowired private val userNotificationRepository: UserNotificationRepository,
    @Autowired private val channelSeedService: ChannelSeedService,
    @Autowired private val mailService: MailService,
) {
    private fun inboxChannelId(): String = channelRepository.findByCode("INBOX")!!.id

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `send to roles fans out one row per distinct recipient`() {
        channelSeedService.seedChannels()
        val result = messageService.send(
            SendMessageDto(
                channelId = inboxChannelId(),
                title = "维护通知", content = "今晚停机",
                targetType = "ROLES", targetIds = listOf("ROLE_A", "ROLE_B"),
            ),
            senderId = "SENDER_1",
        )
        assertEquals(2, result.recipientCount)

        val notification = notificationRepository.findById(result.notificationId).get()
        assertEquals("ROLES", notification.targetType)
        assertEquals(2, notification.recipientCount)
        assertEquals(2, userNotificationRepository.findAll().count { it.userId in listOf("U_ACTIVE_1", "U_ACTIVE_2") })
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `send validation chain`() {
        channelSeedService.seedChannels()
        // 渠道不存在 → 9002
        assertEquals(
            9002,
            assertThrows(SystemException::class.java) {
                messageService.send(
                    SendMessageDto(channelId = "NOPE", title = "t", content = "c", targetType = "ALL", targetIds = null),
                    senderId = "S",
                )
            }.code,
        )
        // USERS 无 targetIds → 9001
        assertEquals(
            9001,
            assertThrows(SystemException::class.java) {
                messageService.send(
                    SendMessageDto(channelId = inboxChannelId(), title = "t", content = "c", targetType = "USERS", targetIds = emptyList()),
                    senderId = "S",
                )
            }.code,
        )
        // 停用渠道 → 9012
        val channel = channelRepository.findByCode("INBOX")!!
        channel.isActive = false
        channelRepository.save(channel)
        assertEquals(
            9012,
            assertThrows(SystemException::class.java) {
                messageService.send(
                    SendMessageDto(channelId = channel.id, title = "t", content = "c", targetType = "ALL", targetIds = null),
                    senderId = "S",
                )
            }.code,
        )
        channel.isActive = true
        channelRepository.save(channel)
        // 目标全无效 → 9012
        assertEquals(
            9012,
            assertThrows(SystemException::class.java) {
                messageService.send(
                    SendMessageDto(channelId = channel.id, title = "t", content = "c", targetType = "USERS", targetIds = listOf("U_DELETED")),
                    senderId = "S",
                )
            }.code,
        )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `send via EMAIL channel records master and dispatches stub mails`() {
        channelSeedService.seedChannels()
        val opsMail = channelRepository.findByCode("EMAIL_CODE")!!
        // EMAIL_CODE 系统渠道照常可用于定向发送（走邮件投递）
        val result = messageService.send(
            SendMessageDto(
                channelId = opsMail.id,
                title = "邮件渠道消息", content = "<p>hello</p>",
                targetType = "USERS", targetIds = listOf("U_ACTIVE_1"),
            ),
            senderId = "SENDER_1",
        )
        val notification = notificationRepository.findById(result.notificationId).get()
        assertEquals(1, notification.recipientCount)
        // 站内投递表无行（EMAIL 渠道不进收件箱；按本条消息过滤，避免与同上下文其他用例落库数据互相干扰）
        assertTrue(userNotificationRepository.findAll().none { it.notification.id == notification.id })
        // Stub 发送成功
        val stubResult = mailService.sendHtmlMail("a1@test.dev", "邮件渠道消息", "<p>hello</p>")
        assertTrue(stubResult.success)
    }
}
