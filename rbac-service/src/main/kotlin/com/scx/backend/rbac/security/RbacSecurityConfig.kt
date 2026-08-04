package com.scx.backend.rbac.security

import com.scx.backend.common.security.AuthContextResolver
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
 * @description 角色权限服务安全配置
 *
 * 网关化后下游服务假定网关已完成鉴权。本配置：
 *  - 无状态会话、关闭 CSRF
 *  - 注册 HeaderAuthFilter 从网关 X-User-* 头解析身份写入 SecurityContext
 *    （供未来 Controller 的 @AuthenticationPrincipal 使用）
 *  - 放行所有请求（强制鉴权集中在网关），含 Swagger 文档直连访问
 *
 * 与 identity 不同，rbac 服务不做本地令牌解析——完全信任网关注入的身份头。
 * 与 file-service 的 FileSecurityConfig 保持一致的安全模型。
 */
@Configuration
@EnableWebSecurity
class RbacSecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // 全放行：鉴权集中在网关。直连访问（如本地 swagger 调试）亦不拦截。
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(
                HeaderAuthFilter(),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter::class.java,
            )
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
 * 下游服务无状态，完全依赖网关注入的头重建认证主体。无令牌时静默跳过（鉴权已由网关完成）。
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
