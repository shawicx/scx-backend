package com.scx.backend.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security 配置
 *
 * 设计：
 *  - 无状态会话（不创建 HttpSession）
 *  - 关闭 CSRF（无状态 API 不需要）
 *  - 启用 CORS（使用统一的 [corsConfigurationSource]，与 WebConfig 保持一致）
 *  - 注册 [TokenAuthenticationFilter] 解析 Bearer token 到 SecurityContext
 *  - 放行 actuator 管理端点（业务端点的鉴权由 [AuthInterceptor] 基于 @Public 注解执行）
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val tokenAuthenticationFilter: TokenAuthenticationFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Actuator 端点放行（业务侧由 @Public 的 /api/health 等互补）
                auth.requestMatchers("/api/actuator/**").permitAll()
                // Swagger / OpenAPI 文档放行
                auth.requestMatchers("/api/swagger-ui/**", "/api/v3/api-docs/**",
                    "/api/swagger-resources/**", "/api/webjars/**").permitAll()
                // 其余请求暂全部放行：业务鉴权由 AuthInterceptor 基于 @Public 注解强制
                // （与源项目 AuthGuard 全局拦截 + @Public 白名单语义一致）
                auth.anyRequest().permitAll()
            }
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * CORS 配置源
     *
     * Spring Security 的过滤器链先于 Spring MVC 执行，preflight（OPTIONS）请求会在到达
     * WebConfig 的 CORS 处理之前被 Security 过滤器拦截。这里在 Security 层注入同一份
     * CORS 配置，保证预检请求能在过滤器链中正确返回 CORS 头。
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Content-Type", "Authorization")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
