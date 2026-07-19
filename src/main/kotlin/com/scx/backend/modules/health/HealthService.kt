package com.scx.backend.modules.health

import com.scx.backend.modules.cache.CacheService
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory

/**
 * 健康检查服务
 *
 * 检查 DB（SELECT 1）+ Redis（set/get/del 探针）+ 系统信息。
 * 与 Actuator 的 /api/actuator/health 不同：这是业务侧自定义检查，返回结构化详情。
 */
@Service
class HealthService(
    private val entityManager: EntityManager,
    private val cacheService: CacheService,
) {
    private val logger = LoggerFactory.getLogger(HealthService::class.java)

    fun checkHealth(): Map<String, Any> {
        val startTime = System.currentTimeMillis()
        return try {
            val dbStatus = checkDatabase()
            val redisStatus = checkRedis()
            val status = if (dbStatus["status"] == "ok" && redisStatus["status"] == "ok") "ok" else "degraded"
            mapOf(
                "service" to "scx-backend",
                "status" to status,
                "timestamp" to java.time.Instant.now().toString(),
                "database" to dbStatus,
                "redis" to redisStatus,
                "system" to getSystemInfo(),
                "responseTime" to "${System.currentTimeMillis() - startTime}ms",
            )
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            mapOf(
                "service" to "scx-backend",
                "status" to "error",
                "timestamp" to java.time.Instant.now().toString(),
                "database" to mapOf("status" to "error", "message" to (e.message ?: "unknown")),
                "redis" to mapOf("status" to "error", "message" to (e.message ?: "unknown")),
                "system" to getSystemInfo(),
                "responseTime" to "${System.currentTimeMillis() - startTime}ms",
            )
        }
    }

    private fun checkDatabase(): Map<String, Any> = try {
        entityManager.createNativeQuery("SELECT 1").singleResult
        mapOf("status" to "ok")
    } catch (e: Exception) {
        logger.error("Database health check failed", e)
        mapOf("status" to "error", "message" to (e.message ?: "unknown"))
    }

    private fun checkRedis(): Map<String, Any> = try {
        val testKey = "health-check-test"
        cacheService.setWithMilliseconds(testKey, "test", 5000)
        val value = cacheService.get<String>(testKey)
        cacheService.del(testKey)
        if (value == "test") {
            mapOf("status" to "ok")
        } else {
            mapOf("status" to "error", "message" to "Redis read/write failed")
        }
    } catch (e: Exception) {
        logger.error("Redis health check failed", e)
        mapOf("status" to "error", "message" to (e.message ?: "unknown"))
    }

    private fun getSystemInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        return mapOf(
            "javaVersion" to System.getProperty("java.version"),
            "platform" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            "uptime" to runtimeMXBean.uptime,
            "availableProcessors" to runtime.availableProcessors(),
            "maxMemory" to runtime.maxMemory(),
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "usedMemory" to (runtime.totalMemory() - runtime.freeMemory()),
        )
    }
}
