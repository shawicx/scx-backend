package com.scx.backend.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.modules.auth.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 安全层集成测试
 * 对标 scx-service: auth.integration.spec.ts
 *
 * 验证完整鉴权链：TokenAuthenticationFilter + AuthInterceptor + @Public
 * 用 JDK 原生 HttpClient 发起真实 HTTP 请求（无第三方测试依赖，Boot 4 环境最稳）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:secTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=security-test-secret",
    ],
)
class SecurityIntegrationTest(
    @Autowired private val authService: AuthService,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val userId = "sec-user-001"
    private val email = "sec@scx.dev"
    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun cleanUp() {
        // 仅清理本测试用户的令牌，避免 flushAll 干扰并行执行的其它测试
        authService.logout(userId)
    }

    private fun get(path: String, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun bodyJson(response: HttpResponse<String>): Map<*, *> =
        objectMapper.readValue(response.body(), Map::class.java)

    @Test
    fun `protected endpoint without token returns 401 with business code 9000`() {
        val resp = get("/api/sec/protected")
        assertEquals(401, resp.statusCode())
        val body = bodyJson(resp)
        assertEquals(false, body["success"])
        assertEquals(9000, body["statusCode"])
        assertEquals("缺少访问令牌", body["message"])
    }

    @Test
    fun `protected endpoint with invalid token returns 401`() {
        val resp = get("/api/sec/protected", token = "invalid.token")
        assertEquals(401, resp.statusCode())
        assertEquals(9000, bodyJson(resp)["statusCode"])
    }

    @Test
    fun `protected endpoint with valid token returns 200`() {
        val token = authService.generateAccessToken(userId, email)
        val resp = get("/api/sec/protected", token)
        assertEquals(200, resp.statusCode())
    }

    @Test
    fun `public endpoint accessible without token`() {
        val resp = get("/api/sec/public")
        assertEquals(200, resp.statusCode())
    }

    @Test
    fun `actuator health endpoint is accessible without token`() {
        val resp = get("/api/actuator/health")
        assertEquals(200, resp.statusCode())
        // actuator 端点不经过 GlobalResponseHandler 包装，status 在顶层
        assertEquals("UP", bodyJson(resp)["status"])
    }

    @Test
    fun `expired token after logout returns 401`() {
        val token = authService.generateAccessToken(userId, email)
        // 先验证有效
        assertEquals(200, get("/api/sec/protected", token).statusCode())
        // 登出后失效
        authService.logout(userId)
        assertEquals(401, get("/api/sec/protected", token).statusCode())
    }
}
