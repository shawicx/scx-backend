package com.scx.backend.security

import com.scx.backend.modules.auth.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Token 认证过滤器（无状态，仅解析）
 *
 * 设计说明：
 *  - 本过滤器在 DispatcherServlet 之前执行，无法获取 handler 注解，因此不在此处强制鉴权。
 *  - 若请求携带有效 Bearer token，解析后将用户信息存入 SecurityContext。
 *  - 强制鉴权（401）由 [AuthInterceptor] 基于 @Public 注解执行，@Public 路由放行。
 */
@Component
class TokenAuthenticationFilter(
    private val authService: AuthService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)
        if (token != null) {
            val payload = authService.validateAccessToken(token)
            if (payload != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    AuthPrincipal(payload.userId, payload.email),
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        val parts = header.split(" ")
        return if (parts.size == 2 && parts[0] == "Bearer") parts[1] else null
    }
}

/**
 * SecurityContext 中存储的认证主体（userId + email）
 */
data class AuthPrincipal(val userId: String, val email: String)
