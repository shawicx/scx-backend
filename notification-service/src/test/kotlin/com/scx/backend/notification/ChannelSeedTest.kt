package com.scx.backend.notification

import com.scx.backend.notification.repository.ChannelRepository
import com.scx.backend.notification.seed.ChannelSeedService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * @description 渠道种子测试：系统三渠道幂等种入
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifySeed;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class ChannelSeedTest(
    @Autowired private val channelRepository: ChannelRepository,
    @Autowired private val channelSeedService: ChannelSeedService,
) {
    @Test
    fun `seed creates three system channels and is idempotent`() {
        // SpringBootTest 启动时 ApplicationRunner 已执行一次；再手动执行验证幂等
        channelSeedService.seedChannels()
        channelSeedService.seedChannels()

        assertEquals(3, channelRepository.count())
        val emailCode = channelRepository.findByCode("EMAIL_CODE")!!
        assertTrue(emailCode.isSystem)
        assertEquals("EMAIL", emailCode.type)
        assertEquals("INBOX", channelRepository.findByCode("INBOX")!!.type)
        assertEquals("ANNOUNCEMENT", channelRepository.findByCode("ANNOUNCEMENT")!!.type)
    }
}
