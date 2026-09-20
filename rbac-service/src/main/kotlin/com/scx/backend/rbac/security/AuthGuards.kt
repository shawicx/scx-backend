package com.scx.backend.rbac.security

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.common.security.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest

/**
 * @description 接口身份守卫（rbac 信任网关注入的 X-User-* 头）
 *
 * 鉴权集中在网关：管理端接口调用 requireAdmin，登录态接口调用 requireUserId。
 * 实现与 notification-service 的 AuthGuards 保持一致。
 */
object AuthGuards {

    /**
     * @description 要求管理员身份，否则抛权限异常
     * @param request HTTP 请求（含网关注入头）
     * @return AuthPrincipal 认证主体
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
