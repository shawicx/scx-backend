package com.scx.backend.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 应用级配置
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** 运行环境，默认 development */
    var env: String = "development",
    /** HTTP 端口，默认 3000 */
    @field:Min(1) var port: Int = 3000,
    /** 是否生产环境（由 env=production 推导） */
    val isProduction: Boolean = false,
)
