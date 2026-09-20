package com.scx.backend.notification.message

import com.scx.backend.common.exception.SystemException
import com.scx.backend.notification.message.dto.SendMessageDto
import com.scx.backend.notification.repository.ChannelRepository
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
 * @description 收件箱服务测试：列表排序、未读分桶、已读标记与越权保护
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifyInbox;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
// 用例间清空 notifications/user_notifications：3 个用例共享上下文库且真实提交（NOT_SUPPORTED），
// 断言为全局计数，任一执行顺序下都会互相污染（实测 markRead → admin → inbox）
@Sql(scripts = ["/sql/users-fixture.sql", "/sql/notifications-cleanup.sql"])
class InboxServiceTest(
    @Autowired private val messageService: MessageService,
    @Autowired private val inboxService: InboxService,
    @Autowired private val channelRepository: ChannelRepository,
    @Autowired private val channelSeedService: ChannelSeedService,
) {
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `inbox lists unread first and announcements pinned first with unread buckets`() {
        channelSeedService.seedChannels()
        val inbox = channelRepository.findByCode("INBOX")!!
        val announce = channelRepository.findByCode("ANNOUNCEMENT")!!

        val n1 = messageService.send(
            SendMessageDto(channelId = inbox.id, title = "第一条", content = "c", targetType = "USERS", targetIds = listOf("U_ACTIVE_1")),
            senderId = "S",
        ).notificationId
        val n2 = messageService.send(
            SendMessageDto(channelId = inbox.id, title = "第二条", content = "c", targetType = "USERS", targetIds = listOf("U_ACTIVE_1")),
            senderId = "S",
        ).notificationId
        messageService.send(
            SendMessageDto(channelId = announce.id, title = "公告", content = "c", targetType = "ALL", targetIds = null, pinned = true),
            senderId = "S",
        )

        // 先读旧的第一条，收件箱应未读在前（第二条），已读在后（第一条）
        inboxService.markRead("U_ACTIVE_1", n1)
        val inboxPage = inboxService.inbox("U_ACTIVE_1", page = 1, limit = 10)
        assertEquals(2, inboxPage.list.size)
        assertEquals(n2, inboxPage.list.first().notificationId)

        // 公告列表独立、置顶含已读状态
        val announcements = inboxService.announcements("U_ACTIVE_1", page = 1, limit = 10)
        assertEquals(1, announcements.list.size)
        assertEquals(true, announcements.list.first().pinned)

        // 未读分桶
        val unread = inboxService.unreadCount("U_ACTIVE_1")
        assertEquals(1L, unread.inbox)
        assertEquals(1L, unread.announcement)

        // read-all 后归零
        inboxService.markAllRead("U_ACTIVE_1", null)
        assertEquals(0L, inboxService.unreadCount("U_ACTIVE_1").inbox + inboxService.unreadCount("U_ACTIVE_1").announcement)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `markRead rejects foreign notification with 9002`() {
        channelSeedService.seedChannels()
        val inbox = channelRepository.findByCode("INBOX")!!
        val sent = messageService.send(
            SendMessageDto(channelId = inbox.id, title = "只给用户1", content = "c", targetType = "USERS", targetIds = listOf("U_ACTIVE_1")),
            senderId = "S",
        ).notificationId
        // U_ACTIVE_2 没有这条投递，按不存在处理
        val ex = assertThrows(SystemException::class.java) { inboxService.markRead("U_ACTIVE_2", sent) }
        assertEquals(9002, ex.code)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `admin list filters by channel and detail exposes audit info`() {
        channelSeedService.seedChannels()
        val announce = channelRepository.findByCode("ANNOUNCEMENT")!!
        val sent = messageService.send(
            SendMessageDto(channelId = announce.id, title = "审计", content = "c", targetType = "ROLES", targetIds = listOf("ROLE_A")),
            senderId = "SENDER_X",
        ).notificationId

        val list = messageService.findAll(page = 1, limit = 10, channelId = announce.id, targetType = null)
        assertEquals(1, list.total)
        val detail = messageService.findById(sent)
        assertEquals("SENDER_X", detail.notification.senderId)
        assertTrue(detail.targetIdList!!.contains("ROLE_A"))
    }
}
