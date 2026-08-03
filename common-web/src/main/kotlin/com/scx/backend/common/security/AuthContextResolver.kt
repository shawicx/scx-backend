package com.scx.backend.common.security

import jakarta.servlet.http.HttpServletRequest

/**
 * @description 认证上下文解析器（下游服务从请求头获取网关注入的用户身份）
 *
 * 微服务化后，鉴权集中在网关。网关验签后将用户信息通过 X-User-* 请求头透传给下游，
 * 下游服务用本解析器从请求头重建 [AuthPrincipal]。
 *
 * 本工具只负责解析请求头（纯 Servlet API，无 Spring Security 依赖），
 * 不涉及 SecurityContext —— SecurityContext 的写入由各服务的 TokenAuthenticationFilter 完成。
 * 因此本类可在 Servlet 服务与无 Security 的场景通用。
 */
object AuthContextResolver {

    /** 网关注入的用户身份请求头（与 gateway 的 AuthGlobalFilter 保持一致） */
    const val HEADER_USER_ID = "X-User-Id"
    const val HEADER_USER_EMAIL = "X-User-Email"
    const val HEADER_USER_ADMIN = "X-User-Admin"

    /**
     * @description 从 X-User-* 请求头解析认证主体
     *
     * 仅解析网关注入的头，不回退到 SecurityContext。供拦截器（已能访问 SecurityContext）
     * 与 TokenAuthenticationFilter（负责写入 SecurityContext）分别按需使用。
     *
     * @param request HTTP 请求
     * @return 认证主体，请求头缺失返回 null
     *
     * @example val principal: AuthPrincipal? = AuthContextResolver.resolveFromHeader(request)
     */
    fun resolveFromHeader(request: HttpServletRequest): AuthPrincipal? {
        val userId = request.getHeader(HEADER_USER_ID) ?: return null
        if (userId.isBlank()) return null
        val email = request.getHeader(HEADER_USER_EMAIL) ?: ""
        val isAdmin = request.getHeader(HEADER_USER_ADMIN)?.equals("true", ignoreCase = true) ?: false
        return AuthPrincipal(userId, email, isAdmin)
    }

    /**
     * @description 从当前请求判断网关是否已标记为管理员
     * @return X-User-Admin 头为 true 返回 true，否则 false
     */
    fun isAdminFromHeader(request: HttpServletRequest): Boolean =
        resolveFromHeader(request)?.isAdmin == true
}
