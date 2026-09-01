package com.scx.backend.common.config

import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @description 启动环境摘要日志：服务就绪后打印「启动成功 + 当前连接的依赖端点」，
 * 用于区分当前连的是本机基础设施（bootRun + 本机 scx-infra）还是 ECS 生产
 * （docker-compose.local.yml 直连远程地址）。
 *
 * 按服务实际持有的配置自适应输出（有 datasource 才打 DB、有 spring.data.redis
 * 才打 Redis、有 minio.endpoint 才打 MinIO……），gateway 额外打印下游服务地址；
 * 各服务经 scanBasePackages("com.scx.backend") 自动装配本组件。
 *
 * 用 ContextRefreshedEvent 而非 ApplicationReadyEvent：common 模块只依赖
 * spring-context，不引入 spring-boot jar。
 */
@Component
class StartupEnvironmentLogger(private val environment: Environment) {

    private val log = LoggerFactory.getLogger(StartupEnvironmentLogger::class.java)
    private val logged = AtomicBoolean(false)

    @EventListener(ContextRefreshedEvent::class)
    fun logStartupSummary() {
        // 上下文层级刷新可能触发多次，只打一次
        if (!logged.compareAndSet(false, true)) return

        val name = environment.getProperty("spring.application.name") ?: "unknown"
        val parts = mutableListOf(
            "环境: ${environment.getProperty("spring.profiles.active") ?: "default"}",
            "端口: ${environment.getProperty("server.port") ?: "-"}",
        )
        // 各依赖仅在本服务声明了对应配置时输出
        dbSummary()?.let { parts += "DB: $it" }
        redisSummary()?.let { parts += "Redis: $it" }
        minioSummary()?.let { parts += "MinIO: $it" }
        mailSummary()?.let { parts += "邮件: $it" }
        if (name == "scx-gateway") parts += "下游: ${downstreamSummary()}"

        log.info("=== ✅ {} 启动成功 | {} ===", name, parts.joinToString(" | "))

        // DB/Redis 指向非本机地址时提示（MinIO 常态即远程，不参与判定）
        val dbHost = dbSummary()?.substringBeforeLast(':')
        val redisHost = redisSummary()?.substringBeforeLast(':')
        val isLocal = { h: String? -> h == null || h == "localhost" || h == "127.0.0.1" }
        if (!isLocal(dbHost) || !isLocal(redisHost)) {
            log.warn("⚠ 当前连接远程基础设施（DB/Redis 非本机地址），注意操作的是生产数据")
        }
    }

    /** 解析 datasource URL（jdbc:postgresql://host:port/db）为 host:port/db */
    private fun dbSummary(): String? {
        val url = environment.getProperty("spring.datasource.url") ?: return null
        return Regex("jdbc:postgresql://([^/:]+):(\\d+)/.+").find(url)?.let {
            "${it.groupValues[1]}:${it.groupValues[2]}/${url.substringAfterLast('/')}"
        } ?: url
    }

    private fun redisSummary(): String? {
        val host = environment.getProperty("spring.data.redis.host") ?: return null
        val port = environment.getProperty("spring.data.redis.port") ?: "6379"
        val db = environment.getProperty("spring.data.redis.database") ?: "0"
        return "$host:$port(db $db)"
    }

    private fun minioSummary(): String? {
        val endpoint = environment.getProperty("minio.endpoint") ?: return null
        val bucket = environment.getProperty("minio.bucket") ?: "-"
        return "$endpoint 桶:$bucket"
    }

    private fun mailSummary(): String? {
        val host = environment.getProperty("spring.mail.host") ?: return null
        val enabled = environment.getProperty("mail.enabled") ?: "true"
        return "$host(${if (enabled.toBoolean()) "SMTP 实发" else "Stub"})"
    }

    private fun downstreamSummary(): String = listOf(
        "identity" to (environment.getProperty("IDENTITY_BASE_URL") ?: "http://localhost:3001"),
        "rbac" to (environment.getProperty("RBAC_BASE_URL") ?: "http://localhost:3002"),
        "notification" to (environment.getProperty("NOTIFICATION_BASE_URL") ?: "http://localhost:3003"),
        "file" to (environment.getProperty("FILE_BASE_URL") ?: "http://localhost:3004"),
    ).joinToString(", ") { "${it.first}=${it.second.removePrefix("http://")}" }
}
