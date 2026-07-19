package com.scx.backend.common.exception

import com.scx.backend.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常处理
 *
 * 处理三类异常：
 * 1. [SystemException] 业务异常 → 业务错误码作为 statusCode，HTTP 状态由映射决定
 * 2. [MethodArgumentNotValidException] 参数校验异常 → 400 + INVALID_PARAMETER
 * 3. 其它兜底异常 → 500
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 业务异常处理：statusCode 使用业务错误码（非 HTTP 码）
     */
    @ExceptionHandler(SystemException::class)
    fun handleSystemException(ex: SystemException, request: HttpServletRequest): ResponseEntity<ApiResponse> {
        val httpStatus = SystemErrorCode.mapToHttpStatus(ex.code)
        logger.error(
            "业务异常: {} | code={} url={} method={} ip={} data={}",
            ex.message,
            ex.code,
            request.requestURI,
            request.method,
            getClientIp(request),
            ex.data,
            ex,
        )
        val body = ApiResponse.error(
            statusCode = ex.code, // 业务错误码
            message = ex.message ?: ex.errorCode.defaultMessage,
            data = ex.data,
            path = request.requestURI,
        )
        return ResponseEntity.status(HttpStatus.valueOf(httpStatus)).body(body)
    }

    /**
     * 参数校验异常 → 400 INVALID_PARAMETER
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "无效") }
        logger.warn("参数校验失败: {} | url={}", fieldErrors, request.requestURI)
        val body = ApiResponse.error(
            statusCode = SystemErrorCode.INVALID_PARAMETER.code,
            message = "请求参数错误",
            data = fieldErrors,
            path = request.requestURI,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    /**
     * 静态资源/路由未找到 → 404
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse> {
        logger.warn("资源未找到: {} | url={}", ex.message, request.requestURI)
        val body = ApiResponse.error(
            statusCode = HttpStatus.NOT_FOUND.value(),
            message = "资源未找到",
            data = null,
            path = request.requestURI,
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    /**
     * 兜底异常处理 → 500
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse> {
        logger.error(
            "未处理异常: {} | url={} method={} ip={}",
            ex.message,
            request.requestURI,
            request.method,
            getClientIp(request),
            ex,
        )
        val body = ApiResponse.error(
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = "服务器内部错误",
            data = null,
            path = request.requestURI,
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private fun getClientIp(request: HttpServletRequest): String =
        request.getHeader("x-forwarded-for")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("x-real-ip")
            ?: request.remoteAddr
            ?: "127.0.0.1"
}
