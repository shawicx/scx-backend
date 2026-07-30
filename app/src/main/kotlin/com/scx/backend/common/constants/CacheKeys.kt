package com.scx.backend.common.constants

/**
 * Redis Key 构造工具，统一管理 key 命名
 */
object CacheKeys {

    fun accessToken(userId: String): String = "access_token:$userId"

    fun refreshToken(userId: String): String = "refresh_token:$userId"

    fun encryptionKey(keyId: String): String = "encryption_key:$keyId"

    fun emailVerification(email: String): String = "email_verification:$email"

    fun loginVerification(email: String): String = "login_verification:$email"
}
