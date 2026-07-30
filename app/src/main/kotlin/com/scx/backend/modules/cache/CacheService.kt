package com.scx.backend.modules.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 缓存服务
 *
 * 封装 Redis 常用操作，get 自动尝试 JSON.parse。
 * 所有方法在异常时记录日志并抛出 RuntimeException。
 */
@Service
class CacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(CacheService::class.java)

    /**
     * 设置缓存值（秒级 TTL）
     * @param key 缓存键
     * @param value 缓存值（string 原样存，其它 JSON 序列化）
     * @param ttlSeconds TTL（秒），null 或 <=0 表示永不过期
     */
    fun set(key: String, value: Any?, ttlSeconds: Long? = null) {
        try {
            val stringValue = if (value is String) value else objectMapper.writeValueAsString(value)
            if (ttlSeconds != null && ttlSeconds > 0) {
                redisTemplate.opsForValue().set(key, stringValue, Duration.ofSeconds(ttlSeconds))
                logger.debug("缓存设置成功: {}, TTL: {}秒", key, ttlSeconds)
            } else {
                redisTemplate.opsForValue().set(key, stringValue)
                logger.debug("缓存设置成功: {}, 无过期时间", key)
            }
        } catch (e: Exception) {
            logger.error("缓存设置失败: {}", key, e)
            throw RuntimeException("Redis设置失败: ${e.message}", e)
        }
    }

    /**
     * 设置缓存值（毫秒级 TTL）
     */
    fun setWithMilliseconds(key: String, value: Any?, ttlMilliseconds: Long) {
        try {
            val stringValue = if (value is String) value else objectMapper.writeValueAsString(value)
            redisTemplate.opsForValue().set(key, stringValue, Duration.ofMillis(ttlMilliseconds))
            logger.debug("缓存设置成功: {}, TTL: {}ms", key, ttlMilliseconds)
        } catch (e: Exception) {
            logger.error("缓存设置失败: {}", key, e)
            throw RuntimeException("Redis设置失败: ${e.message}", e)
        }
    }

    /**
     * 获取缓存值，自动尝试 JSON.parse（失败返回原始字符串）
     * @return 缓存值（解析后的对象或字符串），键不存在返回 null
     */
    @Suppress("UNCHECKED_CAST")  // 泛型擦除导致，由调用方保证类型安全
    fun <T> get(key: String): T? {
        try {
            val value = redisTemplate.opsForValue().get(key)
            if (value == null) {
                logger.debug("缓存获取: {} = null", key)
                return null
            }
            // 尝试解析 JSON
            return try {
                objectMapper.readValue(value, objectMapper.typeFactory.constructType(Any::class.java)) as T
            } catch (e: Exception) {
                logger.debug("缓存获取: {} = {} (string)", key, value)
                value as T
            }
        } catch (e: Exception) {
            logger.error("缓存获取失败: {}", key, e)
            throw RuntimeException("Redis获取失败: ${e.message}", e)
        }
    }

    /** 删除缓存 */
    fun del(key: String) {
        try {
            val result = redisTemplate.delete(key)
            logger.debug("缓存删除: {}, 影响数量: {}", key, result)
        } catch (e: Exception) {
            logger.error("缓存删除失败: {}", key, e)
            throw RuntimeException("Redis删除失败: ${e.message}", e)
        }
    }

    /**
     * 检查键是否存在
     * @return 是否存在
     */
    fun exists(key: String): Boolean {
        return try {
            val result = redisTemplate.hasKey(key)
            logger.debug("缓存存在检查: {} = {}", key, result)
            result
        } catch (e: Exception) {
            logger.error("缓存存在检查失败: {}", key, e)
            throw RuntimeException("Redis存在检查失败: ${e.message}", e)
        }
    }

    /**
     * 获取键的剩余生存时间（秒）
     * @return 剩余 TTL（秒），-1 表示永不过期，-2 表示键不存在
     */
    fun ttl(key: String): Long {
        return try {
            val ttl = redisTemplate.getExpire(key)
            logger.debug("缓存TTL查询: {} = {}秒", key, ttl)
            ttl
        } catch (e: Exception) {
            logger.error("缓存TTL查询失败: {}", key, e)
            throw RuntimeException("RedisTTL查询失败: ${e.message}", e)
        }
    }

    /** 清空当前数据库所有缓存（危险操作） */
    fun flushAll() {
        try {
            redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
            logger.warn("❗ 所有Redis数据已被清空")
        } catch (e: Exception) {
            logger.error("Redis清空失败", e)
            throw RuntimeException("Redis清空失败: ${e.message}", e)
        }
    }

    /**
     * 测试 Redis 连接
     * @return 连接是否正常
     */
    fun testConnection(): Boolean {
        return try {
            val testKey = "connection_test"
            val testValue = "test_value"
            redisTemplate.opsForValue().set(testKey, testValue)
            val retrieved = redisTemplate.opsForValue().get(testKey)
            redisTemplate.delete(testKey)
            val connected = testValue == retrieved
            logger.info("Redis连接测试: {}", if (connected) "成功" else "失败")
            connected
        } catch (e: Exception) {
            logger.error("Redis连接测试失败", e)
            false
        }
    }

    /**
     * 获取 Redis 连接信息
     */
    fun getConnectionInfo(): Map<String, Any> {
        return try {
            val connection = redisTemplate.connectionFactory?.connection
            // Spring Data Redis 4.x 移除了 isOpen；用 ping 探活判断连接状态
            val isOpen = connection?.use { it.ping() } != null
            mapOf(
                "isOpen" to isOpen,
                "isReady" to isOpen,
                "status" to if (isOpen) "ready" else "closed",
            )
        } catch (e: Exception) {
            logger.warn("无法获取Redis连接信息", e)
            mapOf("isOpen" to false, "isReady" to false, "status" to "unknown")
        }
    }

    /** 获取底层 RedisTemplate（供高级操作使用） */
    fun getRedisTemplate(): StringRedisTemplate = redisTemplate
}
