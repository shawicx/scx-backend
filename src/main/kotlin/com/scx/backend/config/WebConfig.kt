package com.scx.backend.config

import com.scx.backend.common.web.AccessLogInterceptor
import com.scx.backend.security.AdminInterceptor
import com.scx.backend.security.AuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC 配置
 *
 * - CORS：允许全部来源、常用方法与头，允许携带凭据
 * - 拦截器：注册鉴权、管理员、访问日志三类拦截器
 */
@Configuration
class WebConfig(
    private val accessLogInterceptor: AccessLogInterceptor,
    private val authInterceptor: AuthInterceptor,
    private val adminInterceptor: AdminInterceptor,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        // 鉴权拦截器（HIGHEST_PRECEDENCE，先于访问日志执行）
        // @Public 放行，其余要求有效 token
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/**")
            .order(Ordered.HIGHEST_PRECEDENCE)
        // 管理员拦截器（检测 @Admin 注解）
        registry.addInterceptor(adminInterceptor)
            .addPathPatterns("/**")
            .order(Ordered.HIGHEST_PRECEDENCE + 1)
        // 访问日志拦截器
        registry.addInterceptor(accessLogInterceptor)
            .addPathPatterns("/**")
    }
}
