package com.scx.backend.modules.mail

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.modules.mail.dto.SendHtmlEmailDto
import com.scx.backend.modules.mail.dto.SendPasswordResetDto
import com.scx.backend.modules.mail.dto.SendVerificationCodeDto
import com.scx.backend.modules.mail.dto.SendWelcomeEmailDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 邮件模块集成测试
 *
 * 1. Thymeleaf 模板渲染验证（不依赖 SMTP）
 * 2. Controller 路由 + StubMailService 返回结构验证（mail.enabled=false）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "mail.enabled=false", // 用 StubMailService，不连真实 SMTP
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:mailTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.name=SCX测试应用",
    ],
)
class MailModuleTest(
    @Autowired private val templateEngine: SpringTemplateEngine,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    // ============ 模板渲染测试 ============

    @Test
    fun `verification-code template renders code and appName`() {
        val ctx = Context().apply {
            setVariable("code", "987654")
            setVariable("appName", "SCX测试应用")
            setVariable("year", 2026)
        }
        val html = templateEngine.process("mail/verification-code", ctx)
        assertTrue(html.contains("987654"), "验证码应渲染到模板")
        assertTrue(html.contains("SCX测试应用"), "应用名应渲染")
        assertTrue(html.contains("2026"), "年份应渲染")
        assertTrue(html.contains("10分钟"), "应包含有效期提醒")
    }

    @Test
    fun `welcome template renders username`() {
        val ctx = Context().apply {
            setVariable("username", "张三")
            setVariable("appName", "SCX测试应用")
            setVariable("year", 2026)
        }
        val html = templateEngine.process("mail/welcome", ctx)
        assertTrue(html.contains("张三"), "用户名应渲染")
        assertTrue(html.contains("欢迎加入"), "应包含欢迎标题")
    }

    @Test
    fun `password-reset template renders resetUrl`() {
        val ctx = Context().apply {
            setVariable("resetToken", "tok123")
            setVariable("resetUrl", "https://example.com/reset?token=tok123")
            setVariable("appName", "SCX测试应用")
            setVariable("year", 2026)
        }
        val html = templateEngine.process("mail/password-reset", ctx)
        assertTrue(html.contains("https://example.com/reset?token=tok123"), "重置链接应渲染")
        assertTrue(html.contains("重置密码"), "应包含重置按钮")
    }

    // ============ Controller 路由测试 ============

    @Suppress("UNCHECKED_CAST")
    private fun dataOf(resp: HttpResponse<String>): Map<String, Any> =
        objectMapper.readValue(resp.body(), Map::class.java)["data"] as Map<String, Any>

    private fun post(path: String, body: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/api$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `send-verification-code returns success with code via stub`() {
        val resp = post("/mail/send-verification-code", """{"email":"test@scx.dev"}""")
        assertEquals(200, resp.statusCode(), resp.body())
        val data = dataOf(resp)
        assertEquals(true, data["success"])
        assertNotNull(data["code"], "stub 应返回验证码")
        assertEquals(6, (data["code"] as String).length, "验证码应为 6 位")
    }

    @Test
    fun `send-verification-code rejects invalid email with 400`() {
        val resp = post("/mail/send-verification-code", """{"email":"not-an-email"}""")
        assertEquals(400, resp.statusCode())
    }

    @Test
    fun `send-welcome-email returns success via stub`() {
        val resp = post("/mail/send-welcome-email", """{"email":"test@scx.dev","username":"测试用户"}""")
        assertEquals(200, resp.statusCode())
        assertEquals(true, dataOf(resp)["success"])
    }

    @Test
    fun `send-password-reset returns success via stub`() {
        val resp = post(
            "/mail/send-password-reset",
            """{"email":"test@scx.dev","resetToken":"tok","resetUrl":"https://x.com/r?t=tok"}""",
        )
        assertEquals(200, resp.statusCode())
    }

    @Test
    fun `send-html-email returns success via stub`() {
        val resp = post(
            "/mail/send-html-email",
            """{"email":"test@scx.dev","subject":"测试主题","html":"<h1>测试</h1>"}""",
        )
        assertEquals(200, resp.statusCode())
    }

    private fun assertNotNull(value: Any?, msg: String) {
        org.junit.jupiter.api.Assertions.assertNotNull(value, msg)
    }
}
