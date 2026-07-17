package com.scx.backend.modules.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * CacheService 集成测试（依赖真实 Redis 127.0.0.1:6388）
 * 验证所有方法行为与源 scx-service CacheService 一致。
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:cacheTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class CacheServiceTest(
    @Autowired private val cacheService: CacheService,
    @Autowired private val objectMapper: ObjectMapper,
) {
    // 每个测试用唯一前缀，避免互相干扰
    private val prefix = "test:${System.currentTimeMillis()}"

    @BeforeEach
    fun cleanUp() {
        cacheService.flushAll()
    }

    @Test
    fun `set and get string value`() {
        cacheService.set("$prefix:str", "hello")
        val value: String? = cacheService.get("$prefix:str")
        assertEquals("hello", value)
    }

    @Test
    fun `get returns null for non-existent key`() {
        val value: String? = cacheService.get<String>("$prefix:nonexistent")
        assertNull(value)
    }

    @Test
    fun `set stores object as JSON and get parses it back`() {
        val data = mapOf("name" to "scx", "age" to 30)
        cacheService.set("$prefix:obj", data)
        val value: Map<String, Any>? = cacheService.get("$prefix:obj")
        assertNotNull(value)
        assertEquals("scx", value!!["name"])
    }

    @Test
    fun `set with seconds TTL expires`() {
        cacheService.set("$prefix:ttl1", "temp", ttlSeconds = 1L)
        assertTrue(cacheService.exists("$prefix:ttl1"))
        Thread.sleep(1200)
        assertFalse(cacheService.exists("$prefix:ttl1"))
    }

    @Test
    fun `setWithoutMilliseconds uses millisecond TTL`() {
        cacheService.setWithMilliseconds("$prefix:ms", "temp", 1000L)
        assertTrue(cacheService.exists("$prefix:ms"))
        assertEquals("temp", cacheService.get<String>("$prefix:ms"))
        Thread.sleep(1100)
        assertNull(cacheService.get<String>("$prefix:ms"))
    }

    @Test
    fun `del removes key`() {
        cacheService.set("$prefix:del", "v")
        assertTrue(cacheService.exists("$prefix:del"))
        cacheService.del("$prefix:del")
        assertFalse(cacheService.exists("$prefix:del"))
    }

    @Test
    fun `exists returns true only for existing keys`() {
        cacheService.set("$prefix:e1", "v")
        assertTrue(cacheService.exists("$prefix:e1"))
        assertFalse(cacheService.exists("$prefix:e2"))
    }

    @Test
    fun `ttl returns remaining seconds for key with expiry`() {
        cacheService.set("$prefix:ttl2", "v", ttlSeconds = 100L)
        val ttl = cacheService.ttl("$prefix:ttl2")
        assertTrue(ttl in 1..100, "expected ttl between 1 and 100, got $ttl")
    }

    @Test
    fun `testConnection returns true`() {
        assertTrue(cacheService.testConnection())
    }

    @Test
    fun `getConnectionInfo returns ready when connected`() {
        val info = cacheService.getConnectionInfo()
        assertEquals("ready", info["status"])
        assertEquals(true, info["isOpen"])
        assertEquals(true, info["isReady"])
    }
}
