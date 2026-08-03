package com.scx.backend.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.security.TokenCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @description 网关配置：注册 TokenCodec Bean（令牌编解码工具，与 identity 共用 JWT_SECRET）
 *
 * TokenCodec 是普通类（非自动配置），需显式声明 Bean。它依赖 ObjectMapper（Spring Boot 自动配置）
 * 与 JWT_SECRET（与 identity 服务共享同一密钥，保证验签一致）。
 */
@Configuration
class GatewayConfig {

    /**
     * @description 构造 TokenCodec
     * @param objectMapper JSON 序列化器（Spring Boot 自动配置）
     * @param secret JWT 密钥，必须与 identity 服务的 jwt.secret 一致
     * @return TokenCodec 实例
     */
    @Bean
    fun tokenCodec(
        objectMapper: ObjectMapper,
        @Value("\${jwt.secret:default-secret}") secret: String,
    ): TokenCodec = TokenCodec(objectMapper, secret)
}
