package com.scx.backend.notification

import com.scx.backend.notification.entity.Channel
import com.scx.backend.notification.repository.ChannelRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * @description 通知数据层基座测试（H2 内存库，实体由 ddl-auto=create-drop 建表）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifyData;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class DataLayerTest(
    @Autowired private val channelRepository: ChannelRepository,
) {
    @Test
    fun `channel save and findByCode roundtrip`() {
        // 渠道种子(ApplicationRunner)会在测试上下文启动时种入 EMAIL_CODE/INBOX/ANNOUNCEMENT，
        // code 列有唯一约束，此处使用非系统 code 验证实体读写往返
        val channel = Channel(
            id = com.scx.backend.common.util.IdGenerator.nextId(),
            code = "TEST_CHANNEL", name = "站内信", type = "INBOX",
            description = "测试", isSystem = true, isActive = true,
        )
        channelRepository.save(channel)
        val found = channelRepository.findByCode("TEST_CHANNEL")
        assertNotNull(found)
        assertEquals("站内信", found!!.name)
        assertEquals(true, found.isSystem)
    }
}
