package com.scx.backend.config

import com.scx.backend.common.web.AccessLogInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC 配置：CORS（对标 scx-service main.ts 的 enableCors 全开策略）
 */
@Configuration
class WebConfig(
    private val accessLogInterceptor: AccessLogInterceptor,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        // 访问日志拦截器，拦截所有请求
        registry.addInterceptor(accessLogInterceptor).addPathPatterns("/**")
    }
}
