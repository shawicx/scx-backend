package com.scx.backend.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Spring Security 配置
 *
 * 对标 scx-service: AuthGuard 作为全局 Guard。
 *
 * 设计：
 *  - 无状态会话（不创建 HttpSession）
 *  - 关闭 CSRF（无状态 API 不需要）
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
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Actuator 端点放行（对标 @Public 的 /api/health 等）
                auth.requestMatchers("/api/actuator/**").permitAll()
                // 其余请求暂全部放行：业务鉴权由 AuthInterceptor 基于 @Public 注解强制
                // （与源项目 AuthGuard 全局拦截 + @Public 白名单语义一致）
                auth.anyRequest().permitAll()
            }
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
