package com.scx.backend.identity.security

import com.scx.backend.common.security.AuthPrincipal
import com.scx.backend.identity.auth.AuthService
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
 *  - 强制鉴权（401）由 AuthInterceptor 基于 @Public 注解执行，@Public 路由放行。
 *
 * 注意：AuthPrincipal 已迁入 common 模块（com.scx.backend.common.security）。
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
                    // isAdmin 来自令牌 payload（Step 5 令牌嵌入角色改造）
                    AuthPrincipal(payload.userId, payload.email, payload.isAdmin),
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
