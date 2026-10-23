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
 * @description AI 模块 HTTP 集成测试：配置掩码保存、空 Key 保留、多会话（新建/归属校验/重命名/删除）与 SSE 流式落库
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
    fun `multi session lifecycle with sse chat persistence and rename`() {
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

        // 1. 无 sessionId 发起对话：新建会话，SSE 先发 session 事件再流式
        val chat = post("/ai/chat", """{"content":"打个招呼"}""", user)
        assertEquals(200, chat.statusCode())
        assertTrue(chat.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"))
        assertTrue(chat.body().contains("event: session"))
        assertTrue(chat.body().contains("event: delta"))
        assertTrue(chat.body().contains("你好"))
        assertTrue(chat.body().contains("，世界"))
        assertTrue(chat.body().contains("event: done"))
        assertEquals("Bearer sk-keep-1234567890", lastAuthHeader.get())
        val sessionId = Regex("\"sessionId\":\"([^\"]+)\"").find(chat.body())!!.groupValues[1]

        // 2. 会话列表：1 条，标题取首条消息前 30 字符
        val sessions = objectMapper.readTree(get("/ai/sessions/list", user).body()).get("data").get("list")
        assertEquals(1, sessions.size())
        assertEquals("打个招呼", sessions.get(0).get("title").asText())

        // 3. 会话内历史：user + assistant，时间正序
        val messages = objectMapper.readTree(get("/ai/messages?sessionId=$sessionId", user).body()).get("data").get("list")
        assertEquals(2, messages.size())
        assertEquals("user", messages.get(0).get("role").asText())
        assertEquals("打个招呼", messages.get(0).get("content").asText())
        assertEquals("assistant", messages.get(1).get("role").asText())
        assertEquals("你好，世界", messages.get(1).get("content").asText())

        // 4. 重命名会话
        val renamed = put(
            "/ai/sessions/update",
            """{"id":"$sessionId","title":"改名后的会话"}""",
            user,
        )
        assertEquals(200, renamed.statusCode())
        assertEquals(
            "改名后的会话",
            objectMapper.readTree(renamed.body()).get("data").get("title").asText(),
        )

        // 5. 携带既有 sessionId 继续对话：消息追加到同一会话
        val chatAgain = post("/ai/chat", """{"sessionId":"$sessionId","content":"再来一句"}""", user)
        assertTrue(chatAgain.body().contains("event: done"))
        val afterSecond = objectMapper.readTree(get("/ai/messages?sessionId=$sessionId", user).body()).get("data").get("list")
        assertEquals(4, afterSecond.size())

        // 6. 归属校验：不存在的会话 9002（他人的会话同样视同不存在）
        val foreign = post(
            "/ai/chat",
            """{"sessionId":"NO_SUCH_SESSION","content":"x"}""",
            mapOf("X-User-Id" to "U_AI_2"),
        )
        assertEquals(404, foreign.statusCode())
        assertEquals(9002, objectMapper.readTree(foreign.body()).get("statusCode").asInt())

        // 7. 删除会话：连同消息一并删除
        val deleted = delete("/ai/sessions/delete?id=$sessionId", user)
        assertTrue(objectMapper.readTree(deleted.body()).get("success").asBoolean())
        val afterDelete = objectMapper.readTree(get("/ai/sessions/list", user).body()).get("data").get("list")
        assertEquals(0, afterDelete.size())
        val orphanMessages = get("/ai/messages?sessionId=$sessionId", user)
        assertEquals(404, orphanMessages.statusCode())

        // 8. 清空全部会话：再建两个会话后一次清空
        val chatB = post("/ai/chat", """{"content":"第二个会话"}""", user)
        val sessionB = Regex("\"sessionId\":\"([^\"]+)\"").find(chatB.body())!!.groupValues[1]
        post("/ai/chat", """{"content":"第三个会话"}""", user)
        assertEquals(2, objectMapper.readTree(get("/ai/sessions/list", user).body()).get("data").get("list").size())
        val clearedAll = post("/ai/messages/clear", "{}", user)
        assertTrue(objectMapper.readTree(clearedAll.body()).get("success").asBoolean())
        assertEquals(0, objectMapper.readTree(get("/ai/sessions/list", user).body()).get("data").get("list").size())
        assertEquals(404, get("/ai/messages?sessionId=$sessionB", user).statusCode())
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/api$path"))
                .header("Content-Type", "application/json")
                .headers(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
                .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun post(path: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("POST", path, body, headers)

    private fun put(path: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("PUT", path, body, headers)

    private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("GET", path, null, headers)

    private fun delete(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        request("DELETE", path, null, headers)

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
