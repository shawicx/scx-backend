package com.scx.backend.common.response

import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * 统一响应封装
 *
 * 成功：success=true，statusCode 为 HTTP 状态码
 * 失败：由 GlobalExceptionHandler 构造，success=false，statusCode 为业务错误码
 */
@JsonPropertyOrder("success", "statusCode", "message", "data", "timestamp", "path")
data class ApiResponse(
    val success: Boolean,
    val statusCode: Int,
    val message: String,
    val data: Any?,
    val timestamp: String,
    val path: String,
) {
    companion object {
        fun success(statusCode: Int, message: String, data: Any?, path: String): ApiResponse =
            ApiResponse(
                success = true,
                statusCode = statusCode,
                message = message,
                data = data,
                timestamp = java.time.Instant.now().toString(),
                path = path,
            )

        fun error(statusCode: Int, message: String, data: Any?, path: String): ApiResponse =
            ApiResponse(
                success = false,
                statusCode = statusCode,
                message = message,
                data = data,
                timestamp = java.time.Instant.now().toString(),
                path = path,
            )
    }
}
