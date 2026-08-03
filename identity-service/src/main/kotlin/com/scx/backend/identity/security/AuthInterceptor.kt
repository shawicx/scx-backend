package com.scx.backend.identity.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.decorator.Public
import com.scx.backend.common.response.ApiResponse
import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.common.security.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 鉴权拦截器（下游服务侧）
 *
 * 在 Handler 解析后执行，能获取方法/类级 @Public 注解：
 *  1. @Public 路由 → 放行
 *  2. 否则要求请求携带有效用户身份（网关 X-User-* 头优先，兜底 SecurityContext）
 *  3. 未认证 → 401 + 统一 ApiResponse（业务码 9000）
 *
 * 微服务化后鉴权集中在网关，本拦截器仅校验身份存在性（由 AuthContextResolver 统一解析）。
 */
@Component
class AuthInterceptor(
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 仅对 Controller 方法做鉴权（静态资源等跳过）
        if (handler !is HandlerMethod) {
            return true
        }

        // 0. 放行 CORS 预检请求（OPTIONS），由 Security 层 CorsFilter 处理跨域头
        if (request.method == "OPTIONS") {
            return true
        }

        // 0. 放行 Swagger / OpenAPI 文档端点（免鉴权）
        val uri = request.requestURI
        if (uri.startsWith("/api/swagger-ui") || uri.startsWith("/api/v3/api-docs") ||
            uri.startsWith("/api/swagger-resources") || uri.startsWith("/api/webjars")
        ) {
            return true
        }

        // 1. 检测 @Public
        val isPublic = handler.hasMethodAnnotation(Public::class.java) ||
            handler.beanType.isAnnotationPresent(Public::class.java)
        if (isPublic) {
            return true
        }

        // 2. 校验用户身份：网关 X-User-* 头优先，兜底 SecurityContext（本地令牌场景）
        val hasIdentity = AuthContextResolver.resolveFromHeader(request) != null ||
            (SecurityContextHolder.getContext()?.authentication?.principal is AuthPrincipal)
        if (!hasIdentity) {
            writeUnauthorized(response, request, "缺少访问令牌")
            return false
        }

        return true
    }

    /**
     * 写入 401 统一响应
     */
    private fun writeUnauthorized(response: HttpServletResponse, request: HttpServletRequest, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ApiResponse.error(
            statusCode = 9000,
            message = message,
            data = null,
            path = request.requestURI,
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
