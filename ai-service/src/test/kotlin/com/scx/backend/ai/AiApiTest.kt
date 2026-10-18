package com.scx.backend.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference

/**
 * @description AI 模块 HTTP 集成测试：配置掩码保存、空 Key 保留、SSE 流式对话与消息落库
 * （直连服务端口，手工注入 X-User-* 头模拟网关；用 JDK HttpServer 伪装 OpenAI 兼容上游）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:aiApi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class AiApiTest(
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {

    private val http = HttpClient.newHttpClient()

    @Test
    fun `chat without config returns 9500`() {
        val response = post(
            "/ai/chat",
            """{"content":"你好"}""",
            mapOf("X-User-Id" to "U_AI_NONE"),
        )
        assertEquals(400, response.statusCode())
        val body = objectMapper.readTree(response.body())
        assertFalse(body.get("success").asBoolean())
        assertEquals(9500, body.get("statusCode").asInt())
    }

    @Test
    fun `config save masks api key and get returns summary`() {
        val user = mapOf("X-User-Id" to "U_AI_1")
        val saved = post(
            "/ai/config/save",
            """{"baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1","apiKey":"sk-test-1234567890","model":"qwen-plus","systemPrompt":"你是助手","temperature":0.7}""",
            user,
        )
        assertEquals(200, saved.statusCode())
        // 明文 Key 绝不能出现在响应中
        assertFalse(saved.body().contains("sk-test-1234567890"))
        val savedData = objectMapper.readTree(saved.body()).get("data")
        assertTrue(savedData.get("hasApiKey").asBoolean())
        assertEquals("sk-…7890", savedData.get("apiKeyMasked").asText())

        val fetched = objectMapper.readTree(get("/ai/config", user).body()).get("data")
        assertEquals("qwen-plus", fetched.get("model").asText())
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", fetched.get("baseUrl").asText())
        assertFalse(get("/ai/config", user).body().contains("sk-test-1234567890"))
    }

    @Test
    fun `blank api key keeps saved one and chat streams with persistence`() {
        val user = mapOf("X-User-Id" to "U_AI_2")

        // 第二次保存 apiKey 传空：应保留第一次的 Key（以伪装上游收到的 Bearer 头验证）
        val first = post(
            "/ai/config/save",
            """{"baseUrl":"http://localhost:${fakeProviderPort}/v1","apiKey":"sk-keep-1234567890","model":"qwen-plus"}""",
            user,
        )
        assertEquals(200, first.statusCode())
        val second = post(
            "/ai/config/save",
            """{"baseUrl":"http://localhost:${fakeProviderPort}/v1","apiKey":"","model":"qwen-max"}""",
            user,
        )
        assertEquals(200, second.statusCode())

        val chat = post("/ai/chat", """{"content":"打个招呼"}""", user)
        assertEquals(200, chat.statusCode())
        assertTrue(chat.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"))
        assertTrue(chat.body().contains("event: delta"))
        assertTrue(chat.body().contains("你好"))
        assertTrue(chat.body().contains("，世界"))
        assertTrue(chat.body().contains("event: done"))
        assertEquals("Bearer sk-keep-1234567890", lastAuthHeader.get())

        // 用户消息 + 助手回复均落库，时间正序
        val messages = objectMapper.readTree(get("/ai/messages", user).body()).get("data").get("list")
        assertEquals(2, messages.size())
        assertEquals("user", messages.get(0).get("role").asText())
        assertEquals("打个招呼", messages.get(0).get("content").asText())
        assertEquals("assistant", messages.get(1).get("role").asText())
        assertEquals("你好，世界", messages.get(1).get("content").asText())

        // 清空会话后历史为空
        val cleared = post("/ai/messages/clear", "{}", user)
        assertTrue(objectMapper.readTree(cleared.body()).get("success").asBoolean())
        val afterClear = objectMapper.readTree(get("/ai/messages", user).body()).get("data").get("list")
        assertEquals(0, afterClear.size())
    }

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

    companion object {
        private val lastAuthHeader = AtomicReference<String?>()

        private var fakeProvider: HttpServer? = null
        private var fakeProviderPort: Int = 0

        @AfterAll
        @JvmStatic
        fun tearDownProvider() {
            fakeProvider?.stop(0)
        }

        /**
         * 伪装 OpenAI 兼容上游：/models 返回 200；/chat/completions 返回固定 SSE 流
         */
        private fun startFakeProvider(): Int {
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.createContext("/") { exchange ->
                when {
                    exchange.requestURI.path.endsWith("/models") -> {
                        lastAuthHeader.set(exchange.requestHeaders.getFirst("Authorization"))
                        val bytes = """{"data":[]}""".toByteArray()
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }

                    exchange.requestURI.path.endsWith("/chat/completions") -> {
                        lastAuthHeader.set(exchange.requestHeaders.getFirst("Authorization"))
                        val stream = (
                            "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n" +
                                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n" +
                                "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}\n\n" +
                                "data: [DONE]\n\n"
                            ).toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.add("Content-Type", "text/event-stream")
                        exchange.sendResponseHeaders(200, 0)
                        exchange.responseBody.use {
                            it.write(stream)
                            it.flush()
                        }
                    }

                    else -> exchange.sendResponseHeaders(404, -1)
                }
            }
            server.start()
            fakeProvider = server
            return server.address.port
        }

        init {
            fakeProviderPort = startFakeProvider()
        }
    }
}
