package com.scx.backend.common.constants

/**
 * 统一的 TTL 常量（毫秒）
 * 对标 scx-service: src/common/utils/ttl.constants.ts
 */
object TtlConstants {
    /** Access Token 有效期：2 小时 */
    const val ACCESS_TOKEN_TTL_MS: Long = 2 * 60 * 60 * 1000L

    /** Refresh Token 有效期：7 天 */
    const val REFRESH_TOKEN_TTL_MS: Long = 7 * 24 * 60 * 60 * 1000L

    /** 前端密码加密密钥有效期：5 分钟 */
    const val ENCRYPTION_KEY_TTL_MS: Long = 5 * 60 * 1000L

    /** 注册验证码有效期：10 分钟 */
    const val EMAIL_VERIFICATION_TTL_MS: Long = 10 * 60 * 1000L

    /** 登录验证码有效期：10 分钟 */
    const val LOGIN_VERIFICATION_TTL_MS: Long = 10 * 60 * 1000L
}
