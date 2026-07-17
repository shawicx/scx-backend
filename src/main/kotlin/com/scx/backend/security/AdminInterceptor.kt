package com.scx.backend.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.response.ApiResponse
import com.scx.backend.common.exception.SystemErrorCode
import com.scx.backend.modules.user.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 管理员鉴权拦截器
 * 对标 scx-service: src/common/guards/admin.guard.ts
 *
 * 检测方法/类级 @Admin 注解，校验当前用户是否为管理员（SUPER_ADMIN 或 code 以 ADMIN 开头）。
 * 非管理员 → 401（对标源 UnauthorizedException '需要管理员权限'）
 */
@Component
class AdminInterceptor(
    private val userService: UserService,
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        val isAdminRequired = handler.hasMethodAnnotation(Admin::class.java) ||
            handler.beanType.isAnnotationPresent(Admin::class.java)
        if (!isAdminRequired) return true

        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal as? AuthPrincipal
            ?: run {
                writeUnauthorized(response, request, "未找到用户信息")
                return false
            }

        if (!userService.isAdmin(principal.userId)) {
            writeUnauthorized(response, request, "需要管理员权限")
            return false
        }
        return true
    }

    private fun writeUnauthorized(response: HttpServletResponse, request: HttpServletRequest, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ApiResponse.error(
            statusCode = SystemErrorCode.MISSING_TOKEN.code,
            message = message,
            data = null,
            path = request.requestURI,
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
