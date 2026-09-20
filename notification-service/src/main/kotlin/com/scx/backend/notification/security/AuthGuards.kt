package com.scx.backend.notification.security

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.common.security.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest

/**
 * @description 接口身份守卫（notification 无 Spring Security，直接解析网关注入的 X-User-* 头）
 *
 * 鉴权集中在网关：管理端接口调用 requireAdmin，用户端接口调用 requireUserId。
 */
object AuthGuards {

    /**
     * @description 要求管理员身份，否则抛权限异常
     * @param request HTTP 请求（含网关注入头）
     * @return AuthPrincipal 认证主体（供记录 senderId 等）
     */
    fun requireAdmin(request: HttpServletRequest): AuthPrincipal {
        val principal = AuthContextResolver.resolveFromHeader(request)
            ?: throw SystemException.missingToken()
        if (!principal.isAdmin) throw SystemException.insufficientPermission()
        return principal
    }

    /**
     * @description 要求已登录，返回当前用户 ID
     * @param request HTTP 请求
     * @returns String 用户 ID（ULID）
     */
    fun requireUserId(request: HttpServletRequest): String =
        AuthContextResolver.resolveFromHeader(request)?.userId
            ?: throw SystemException.missingToken()
}
