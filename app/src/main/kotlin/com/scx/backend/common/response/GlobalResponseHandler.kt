package com.scx.backend.common.response

import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * 统一响应包装
 *
 * 规则：
 * 1. 若 body 已是 [ApiResponse]（异常处理器或手动构造），原样返回不重复包装
 * 2. 若 body 是 Map 且含 "message" 键：提取 message 作为响应消息；
 *    若除 message 外无其他字段，data=null；否则 data 为剩余字段
 * 3. 其它情况：message 取默认成功消息（按 HTTP 状态码），data=原 body
 */
@RestControllerAdvice
class GlobalResponseHandler : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>): Boolean =
        true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        // 已是 ApiResponse 则不重复包装
        if (body is ApiResponse) {
            return body
        }

        val servletRequest = (request as ServletServerHttpRequest).servletRequest
        val path = servletRequest.requestURI

        // 排除非业务端点：Swagger 文档、Actuator、静态资源（不包装为 ApiResponse）
        if (path.startsWith("/api/v3/api-docs") ||
            path.startsWith("/api/swagger") ||
            path.startsWith("/api/webjars") ||
            path.startsWith("/api/actuator")
        ) {
            return body
        }

        val status = (response as ServletServerHttpResponse).servletResponse.status

        // 处理含 message 字段的对象（提取 message 作为响应消息，剩余字段作为 data）
        if (body is Map<*, *>) {
            val message = body["message"]
            if (message is String) {
                val rest = body.filterKeys { it != "message" }
                val data: Any? = if (rest.isEmpty()) null else rest
                return ApiResponse.success(status, message, data, path)
            }
        }

        // 默认：用成功消息 + 原始 body 作为 data
        val successMessage = getSuccessMessage(status)
        return ApiResponse.success(status, successMessage, body, path)
    }

    private fun getSuccessMessage(statusCode: Int): String = when (statusCode) {
        200, 201 -> "请求成功"
        202 -> "请求已接受"
        204 -> "操作成功"
        else -> "操作成功"
    }
}
