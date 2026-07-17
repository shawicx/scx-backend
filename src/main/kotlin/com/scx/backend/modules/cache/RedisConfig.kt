package com.scx.backend.modules.cache

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 配置
 * 对标 scx-service: src/modules/cache/cache.module.ts 的客户端工厂
 *
 * StringRedisTemplate 由 Spring Boot 自动配置注入（application.yml 的 spring.data.redis.*），
 * 此处显式声明便于后续扩展（如自定义序列化、连接池等）。
 *
 * 连接/超时/重连策略对标源项目：
 *  - connectTimeout 10s
 *  - commandTimeout 5s
 *  - Lettuce 默认自带指数退避重连
 */
@Configuration
class RedisConfig {

    private val logger = LoggerFactory.getLogger(RedisConfig::class.java)

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        logger.info("初始化 StringRedisTemplate，连接工厂: ${connectionFactory::class.simpleName}")
        return StringRedisTemplate(connectionFactory)
    }
}
