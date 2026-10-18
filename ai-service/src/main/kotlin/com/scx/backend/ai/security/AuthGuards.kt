package com.scx.backend.ai.security

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.common.security.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest

/**
 * @description 接口身份守卫（ai-service 无 Spring Security，直接解析网关注入的 X-User-* 头）
 *
 * 鉴权集中在网关：本服务全部为用户级接口，统一调用 requireUserId。
 */
object AuthGuards {

    /**
     * @description 要求已登录，返回当前用户主体
     * @param request HTTP 请求（含网关注入头）
     * @return AuthPrincipal 认证主体
     */
    fun requirePrincipal(request: HttpServletRequest): AuthPrincipal =
        AuthContextResolver.resolveFromHeader(request)
            ?: throw SystemException.missingToken()

    /**
     * @description 要求已登录，返回当前用户 ID
     * @param request HTTP 请求
     * @return String 用户 ID（ULID）
     */
    fun requireUserId(request: HttpServletRequest): String =
        requirePrincipal(request).userId
}
