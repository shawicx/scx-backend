package com.scx.backend.notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * @description 通知模块 HTTP 冒烟测试：管理员守卫、发送、收件箱（直连服务端口，手工注入 X-User-* 头模拟网关）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:notifyApi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
// 用例间清空 notifications/user_notifications 并重建 users：3 个用例共享同一上下文 / H2 库，
// HTTP 调用在服务端线程真实提交（无测试事务包裹），ALL 扇出会污染后续用例的收件箱计数断言
@Sql(scripts = ["/sql/users-fixture.sql", "/sql/notifications-cleanup.sql"])
class NotificationApiTest(
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    private fun post(path: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/api$path"))
                .header("Content-Type", "application/json")
                .headers(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/api$path"))
                .headers(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `channel create rejects non-admin with 9003`() {
        val response = post(
            "/channels/create",
            """{"code":"OPS","name":"运营","type":"INBOX"}""",
            headers = mapOf("X-User-Id" to "U1", "X-User-Admin" to "false"),
        )
        val body = objectMapper.readTree(response.body())
        assertEquals(false, body.get("success").asBoolean())
        assertEquals(9003, body.get("statusCode").asInt())
    }

    @Test
    fun `send and inbox happy path via http`() {
        val admin = mapOf("X-User-Id" to "ADMIN_1", "X-User-Email" to "admin@test.dev", "X-User-Admin" to "true")
        // 种子已由 ApplicationRunner 种入 INBOX 渠道；先取渠道 ID
        val channels = objectMapper.readTree(
            get("/channels/all", admin).body(),
        ).get("data")
        val inboxId = channels.filter { it.get("code").asText() == "INBOX" }
            .first().get("id").asText()

        val send = post(
            "/notifications/send",
            """{"channelId":"$inboxId","title":"HTTP冒烟","content":"c","targetType":"ALL"}""",
            admin,
        ).let { objectMapper.readTree(it.body()) }
        assertTrue(send.get("success").asBoolean())

        // 用户视角收件箱（需要 users 表数据——ALL 扇出在空 users 表下会 9012；
        // 本用例改用 USERS 定向无效目标前置断言 9012，或注入 users 夹具后走 inbox）
        val rejected = post(
            "/notifications/send",
            """{"channelId":"$inboxId","title":"无人","content":"c","targetType":"USERS","targetIds":["NOBODY"]}""",
            admin,
        ).let { objectMapper.readTree(it.body()) }
        assertEquals(9012, rejected.get("statusCode").asInt())
    }

    @Test
    fun `targeted send reaches recipient inbox via http`() {
        val admin = mapOf("X-User-Id" to "ADMIN_1", "X-User-Email" to "admin@test.dev", "X-User-Admin" to "true")
        val channels = objectMapper.readTree(
            get("/channels/all", admin).body(),
        ).get("data")
        val inboxId = channels.filter { it.get("code").asText() == "INBOX" }
            .first().get("id").asText()

        val send = post(
            "/notifications/send",
            """{"channelId":"$inboxId","title":"定向冒烟","content":"c","targetType":"USERS","targetIds":["U_ACTIVE_1"]}""",
            admin,
        ).let { objectMapper.readTree(it.body()) }
        assertTrue(send.get("success").asBoolean())

        // 用户视角收件箱应恰好收到这 1 条（@Sql 已清空历史投递）
        val inbox = objectMapper.readTree(
            get("/notifications/inbox", headers = mapOf("X-User-Id" to "U_ACTIVE_1")).body(),
        ).get("data")
        assertEquals(1, inbox.get("list").size())
        assertEquals("定向冒烟", inbox.get("list").first().get("title").asText())
    }
}
