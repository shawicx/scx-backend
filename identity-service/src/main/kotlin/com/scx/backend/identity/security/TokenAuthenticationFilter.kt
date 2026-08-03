package com.scx.backend.identity.security

import com.scx.backend.common.security.AuthContextResolver
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
 * Token 认证过滤器（无状态，仅解析，不强制鉴权）
 *
 * 设计说明：
 *  - 本过滤器在 DispatcherServlet 之前执行，无法获取 handler 注解，因此不在此处强制鉴权。
 *  - 强制鉴权（401）由 AuthInterceptor 基于 @Public 注解执行。
 *
 * 身份解析优先级（Step 6 网关化后）：
 *  1. 网关注入的 X-User-* 头 → 直接构建 SecurityContext（生产场景，无需本地验签）
 *  2. 本地 Bearer token → 调 AuthService 解析后构建（过渡期直连 identity 调试场景）
 *
 * 将身份写入 SecurityContext，使 Controller 的 @AuthenticationPrincipal 在两种场景都可用。
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
        // 1. 优先读网关注入的头（无需 AuthService，零开销）
        val headerPrincipal = AuthContextResolver.resolveFromHeader(request)
        if (headerPrincipal != null) {
            setAuthentication(headerPrincipal)
        } else {
            // 2. 兜底：本地解析 Bearer token（直连调试场景）
            val token = extractToken(request)
            if (token != null) {
                val payload = authService.validateAccessToken(token)
                if (payload != null) {
                    setAuthentication(AuthPrincipal(payload.userId, payload.email, payload.isAdmin))
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun setAuthentication(principal: AuthPrincipal) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        val parts = header.split(" ")
        return if (parts.size == 2 && parts[0] == "Bearer") parts[1] else null
    }
}
