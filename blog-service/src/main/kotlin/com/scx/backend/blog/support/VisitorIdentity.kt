package com.scx.backend.blog.support

import com.scx.backend.common.util.IpUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * @description 匿名访客标识派生组件（服务端掌控，不信任客户端提交）
 *
 * visitorKeyHash = SHA-256(ip + User-Agent + salt)、ipHash = SHA-256(ip + salt)，
 * 分别用于点赞去重与评论限流的脱敏标识。盐需保持稳定（保证同一访客标识
 * 持续一致），仅降低重复操作，不承诺抵御多设备刷量。
 */
@Component
class VisitorIdentity(
    @Value("\${blog.visitor-salt}") private val salt: String,
) {

    /**
     * @description 派生匿名访客标识哈希（点赞去重）
     * @param request HTTP 请求
     * @return String 64 位十六进制哈希
     */
    fun visitorKeyHash(request: HttpServletRequest): String =
        sha256("${IpUtils.getClientIp(request)}|${request.getHeader("User-Agent") ?: ""}|$salt")

    /**
     * @description 派生客户端 IP 哈希（评论限流，不存明文 IP）
     * @param request HTTP 请求
     * @return String 64 位十六进制哈希
     */
    fun ipHash(request: HttpServletRequest): String =
        sha256("${IpUtils.getClientIp(request)}|$salt")

    /**
     * @description 计算字符串 SHA-256 十六进制摘要
     * @param input 输入字符串
     * @return String 64 位十六进制摘要
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
