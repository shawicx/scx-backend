package com.scx.backend.notification.channel

import com.scx.backend.common.exception.SystemException
import com.scx.backend.notification.channel.dto.CreateChannelDto
import com.scx.backend.notification.channel.dto.UpdateChannelDto
import com.scx.backend.notification.repository.ChannelRepository
import com.scx.backend.notification.seed.ChannelSeedService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * @description 渠道管理服务测试：唯一性、系统渠道保护、分页
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifyChannel;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class ChannelServiceTest(
    @Autowired private val channelService: ChannelService,
    @Autowired private val channelRepository: ChannelRepository,
    @Autowired private val channelSeedService: ChannelSeedService,
) {
    @Test
    fun `create channel ok and duplicate code rejected`() {
        val created = channelService.create(
            CreateChannelDto(code = "OPS_MAIL", name = "运营邮件", type = "EMAIL", description = null),
        )
        assertEquals("EMAIL", created.type)

        val ex = assertThrows(SystemException::class.java) {
            channelService.create(CreateChannelDto(code = "OPS_MAIL", name = "重复", type = "INBOX", description = null))
        }
        assertEquals(9007, ex.code)
    }

    @Test
    fun `system channel cannot be deleted but can be renamed or disabled`() {
        channelSeedService.seedChannels()
        val inbox = channelRepository.findByCode("INBOX")!!

        val updated = channelService.update(
            UpdateChannelDto(id = inbox.id, name = "站内信（新）", description = "改描述", isActive = false),
        )
        assertEquals("站内信（新）", updated.name)
        assertEquals(false, updated.isActive)
        assertEquals("INBOX", updated.type) // type 不可改（DTO 不含 type 字段）

        val ex = assertThrows(SystemException::class.java) { channelService.delete(inbox.id) }
        assertEquals(9012, ex.code)

        // 恢复，避免影响其他用例
        channelService.update(UpdateChannelDto(id = inbox.id, isActive = true))
    }

    @Test
    fun `delete unknown channel throws 9002 and list is pageable`() {
        val ex = assertThrows(SystemException::class.java) { channelService.delete("NOT_EXIST") }
        assertEquals(9002, ex.code)

        val page = channelService.findAll(page = 1, limit = 2, type = null, keyword = null)
        assertEquals(1, page.page)
        assertTrue(page.limit == 2)
        assertNotNull(page.total)
    }
}
