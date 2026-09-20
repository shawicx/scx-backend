package com.scx.backend.notification.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.TestPropertySource

/**
 * @description 收件人解析测试（原生 SQL 读 users/user_roles，夹具见 users-fixture.sql）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifyResolver;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
@Sql("/sql/users-fixture.sql")
class RecipientResolverTest(
    @Autowired private val recipientResolver: RecipientResolver,
) {
    @Test
    fun `resolveAll returns active non-deleted users only`() {
        val ids = recipientResolver.resolveAll()
        assertEquals(listOf("U_ACTIVE_1", "U_ACTIVE_2"), ids.sorted())
    }

    @Test
    fun `resolveByRoleIds deduplicates users with multiple roles`() {
        // U_ACTIVE_1 同时具有 ROLE_A 与 ROLE_B，DISTINCT 后只出现一次
        val ids = recipientResolver.resolveByRoleIds(listOf("ROLE_A", "ROLE_B")).sorted()
        assertEquals(listOf("U_ACTIVE_1", "U_ACTIVE_2"), ids)
    }

    @Test
    fun `resolveByUserIds filters invalid users`() {
        val ids = recipientResolver.resolveByUserIds(listOf("U_ACTIVE_1", "U_INACTIVE", "U_DELETED", "U_MISSING")).sorted()
        assertEquals(listOf("U_ACTIVE_1"), ids)
    }

    @Test
    fun `resolveEmails maps userId to email`() {
        val emails = recipientResolver.resolveEmails(listOf("U_ACTIVE_1", "U_ACTIVE_2"))
        assertEquals("a1@test.dev", emails["U_ACTIVE_1"])
        assertEquals("a2@test.dev", emails["U_ACTIVE_2"])
        assertFalse(emails.containsKey("U_INACTIVE"))
    }
}
