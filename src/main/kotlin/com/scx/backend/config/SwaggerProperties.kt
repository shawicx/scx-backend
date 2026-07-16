package com.scx.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Swagger 文档配置（对标 scx-service 的 swaggerConfig）
 */
@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    var enabled: Boolean = true,
    var title: String = "SCX Backend API",
    var description: String = "SCX Backend API Documentation",
    var version: String = "1.0",
    var path: String = "api/docs",
)
