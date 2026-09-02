package com.scx.backend.commonaudit

import com.scx.backend.common.util.IpUtils
import jakarta.servlet.http.HttpServletRequest

/**
 * @description 客户端上下文信息（IP + User-Agent）
 *
 * Controller 从当前请求提取后传入 Service，供日志记录使用，
 * 避免业务方法直接依赖 HttpServletRequest。
 *
 * @param ip 客户端 IP（x-forwarded-for 首段 > x-real-ip > remoteAddr）
 * @param userAgent 客户端 User-Agent（原样存储）
 */
data class ClientInfo(
    val ip: String? = null,
    val userAgent: String? = null,
)

/**
 * @description 从当前请求提取客户端上下文
 * @param receiver HTTP 请求
 * @returns ClientInfo 客户端 IP 与 User-Agent
 *
 * @example userService.loginWithEmailCode(dto, request.toClientInfo())
 */
fun HttpServletRequest.toClientInfo(): ClientInfo = ClientInfo(
    ip = IpUtils.getClientIp(this),
    userAgent = this.getHeader("user-agent"),
)
