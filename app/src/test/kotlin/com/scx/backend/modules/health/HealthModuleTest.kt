package com.scx.backend.modules.health

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
 * 健康检查模块测试
 * 验证 /api/health 业务侧端点（DB + Redis + 系统信息）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:healthTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class HealthModuleTest(
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    @Suppress("UNCHECKED_CAST")
    private fun data(resp: HttpResponse<String>): Map<String, Any> =
        objectMapper.readValue(resp.body(), Map::class.java)["data"] as Map<String, Any>

    @Test
    fun `health endpoint returns service status and components`() {
        val resp = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/api/health"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, resp.statusCode())
        val d = data(resp)
        assertEquals("scx-backend", d["service"])
        assertNotNull(d["status"])
        assertNotNull(d["timestamp"])
        assertNotNull(d["responseTime"])
        // 组件存在
        @Suppress("UNCHECKED_CAST")
        val database = d["database"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val redis = d["redis"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val system = d["system"] as Map<String, Any>
        assertNotNull(database["status"])
        assertNotNull(redis["status"])
        assertTrue(system.containsKey("javaVersion"))
        assertTrue(system.containsKey("uptime"))
    }
}
