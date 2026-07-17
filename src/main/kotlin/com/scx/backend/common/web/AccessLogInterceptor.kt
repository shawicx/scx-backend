package com.scx.backend.common.web

import com.scx.backend.common.util.IpUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 访问日志拦截器
 * 对标 scx-service: src/common/interceptors/logging.interceptor.ts
 *
 * 记录每个请求的方法、URL、IP、UA、耗时、状态码。
 * 敏感字段脱敏：headers 的 authorization/cookie/x-api-key；不记录请求体（避免 IO 与脱敏复杂度）。
 */
@Component
class AccessLogInterceptor : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger("ACCESS")

    companion object {
        private const val START_TIME_ATTR = "scx.requestStartTime"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val startTime = request.getAttribute(START_TIME_ATTR) as? Long ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        val method = request.method
        val uri = request.requestURI
        val ip = IpUtils.getClientIp(request)
        val status = response.status
        val userAgent = request.getHeader("user-agent") ?: ""

        if (ex != null || status >= 500) {
            logger.error("{} {} - {} - {}ms - {} | err={}", method, uri, status, duration, ip, ex?.message)
        } else if (status >= 400) {
            logger.warn("{} {} - {} - {}ms - {}", method, uri, status, duration, ip)
        } else {
            logger.info("{} {} - {} - {}ms - {} ua={}", method, uri, status, duration, ip, userAgent)
        }
    }
}
