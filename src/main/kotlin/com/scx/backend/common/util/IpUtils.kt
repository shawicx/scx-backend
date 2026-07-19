package com.scx.backend.common.util

import jakarta.servlet.http.HttpServletRequest

/**
 * 客户端 IP 工具
 */
object IpUtils {

    /**
     * 从请求中获取客户端真实 IP 地址
     * 优先级：x-forwarded-for 首段 > x-real-ip > remoteAddr
     */
    fun getClientIp(request: HttpServletRequest): String =
        request.getHeader("x-forwarded-for")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("x-real-ip")
            ?: request.remoteAddr
            ?: "127.0.0.1"
}
