package com.scx.backend.file.security

import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.common.security.AuthPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * @description 文件服务安全配置
 *
 * 网关化后下游服务假定网关已完成鉴权。本配置：
 *  - 无状态会话、关闭 CSRF
 *  - 注册 HeaderAuthFilter 从网关 X-User-* 头解析身份写入 SecurityContext（供 @AuthenticationPrincipal 使用）
 *  - 放行所有请求（强制鉴权集中在网关）
 *
 * 与 identity 不同，文件服务不做本地令牌解析与 DB 管理员回查——完全信任网关注入的身份头。
 */
@Configuration
@EnableWebSecurity
class FileSecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(HeaderAuthFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOriginPatterns = listOf("*")
                    allowedMethods = listOf("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("Content-Type", "Authorization")
                    allowCredentials = true
                },
            )
        }
}

/**
 * @description 从网关 X-User-* 头解析身份写入 SecurityContext 的过滤器
 *
 * 下游服务无状态，完全依赖网关注入的头重建认证主体，使 Controller 的
 * @AuthenticationPrincipal 可用。无令牌时静默跳过（鉴权已由网关完成）。
 */
@Component
class HeaderAuthFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        AuthContextResolver.resolveFromHeader(request)?.let { principal ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
        }
        filterChain.doFilter(request, response)
    }
}
