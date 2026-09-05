package com.scx.backend.identity.health

import com.scx.backend.identity.cache.CacheService
import com.scx.backend.identity.health.dto.ComponentHealthDto
import com.scx.backend.identity.health.dto.HealthResponseDto
import com.scx.backend.identity.health.dto.SystemInfoDto
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

    fun checkHealth(): HealthResponseDto {
        val startTime = System.currentTimeMillis()
        return try {
            val dbStatus = checkDatabase()
            val redisStatus = checkRedis()
            val status = if (dbStatus.status == "ok" && redisStatus.status == "ok") "ok" else "degraded"
            HealthResponseDto(
                service = "scx-backend",
                status = status,
                timestamp = java.time.Instant.now().toString(),
                database = dbStatus,
                redis = redisStatus,
                system = getSystemInfo(),
                responseTime = "${System.currentTimeMillis() - startTime}ms",
            )
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            HealthResponseDto(
                service = "scx-backend",
                status = "error",
                timestamp = java.time.Instant.now().toString(),
                database = ComponentHealthDto("error", e.message ?: "unknown"),
                redis = ComponentHealthDto("error", e.message ?: "unknown"),
                system = getSystemInfo(),
                responseTime = "${System.currentTimeMillis() - startTime}ms",
            )
        }
    }

    private fun checkDatabase(): ComponentHealthDto = try {
        entityManager.createNativeQuery("SELECT 1").singleResult
        ComponentHealthDto("ok")
    } catch (e: Exception) {
        logger.error("Database health check failed", e)
        ComponentHealthDto("error", e.message ?: "unknown")
    }

    private fun checkRedis(): ComponentHealthDto = try {
        val testKey = "health-check-test"
        cacheService.setWithMilliseconds(testKey, "test", 5000)
        val value = cacheService.get<String>(testKey)
        cacheService.del(testKey)
        if (value == "test") {
            ComponentHealthDto("ok")
        } else {
            ComponentHealthDto("error", "Redis read/write failed")
        }
    } catch (e: Exception) {
        logger.error("Redis health check failed", e)
        ComponentHealthDto("error", e.message ?: "unknown")
    }

    private fun getSystemInfo(): SystemInfoDto {
        val runtime = Runtime.getRuntime()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        return SystemInfoDto(
            javaVersion = System.getProperty("java.version"),
            platform = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            uptime = runtimeMXBean.uptime,
            availableProcessors = runtime.availableProcessors(),
            maxMemory = runtime.maxMemory(),
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            usedMemory = (runtime.totalMemory() - runtime.freeMemory()),
        )
    }
}
