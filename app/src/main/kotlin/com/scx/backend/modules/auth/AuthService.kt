package com.scx.backend.modules.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.constants.TtlConstants
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.modules.cache.CacheService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 认证服务
 *
 * 自研令牌协议（非标准 JWT）：
 *  token = base64(JSON({userId,email,type,timestamp})) + "." + hexHmac
 *
 * 关键点：
 *  - base64 使用标准编码
 *  - JSON 字段顺序固定为 userId, email, type, timestamp（用 LinkedHashMap 保序）
 *  - HMAC-SHA256，密钥来自 JWT_SECRET 环境变量
 *  - 单点令牌：Redis 中缓存的令牌必须与请求令牌完全相等
 */
@Service
class AuthService(
    private val cacheService: CacheService,
    private val objectMapper: ObjectMapper,
    @Value("\${jwt.secret:default-secret}") private val jwtSecret: String,
) {
    /**
     * 生成访问令牌（有效期 2 小时）
     */
    fun generateAccessToken(userId: String, email: String): String {
        val token = createToken(userId, email, "access")
        cacheService.setWithMilliseconds(
            CacheKeys.accessToken(userId),
            token,
            TtlConstants.ACCESS_TOKEN_TTL_MS,
        )
        return token
    }

    /**
     * 生成刷新令牌（有效期 7 天）
     */
    fun generateRefreshToken(userId: String, email: String): String {
        val token = createToken(userId, email, "refresh")
        cacheService.setWithMilliseconds(
            CacheKeys.refreshToken(userId),
            token,
            TtlConstants.REFRESH_TOKEN_TTL_MS,
        )
        return token
    }

    /**
     * 验证访问令牌
     * @return 用户信息，验证失败返回 null
     */
    fun validateAccessToken(token: String): TokenPayload? = validateToken(token, "access", CacheKeys::accessToken)

    /**
     * 验证刷新令牌
     * @return 用户信息，验证失败返回 null
     */
    fun validateRefreshToken(token: String): TokenPayload? = validateToken(token, "refresh", CacheKeys::refreshToken)

    /**
     * 刷新令牌对
     * @return 新的 accessToken + refreshToken，验证失败返回 null
     */
    fun refreshTokens(refreshToken: String): TokenPair? {
        val userInfo = validateRefreshToken(refreshToken) ?: return null
        return TokenPair(
            accessToken = generateAccessToken(userInfo.userId, userInfo.email),
            refreshToken = generateRefreshToken(userInfo.userId, email = userInfo.email),
        )
    }

    /**
     * 登出（删除该用户的 access/refresh 令牌）
     */
    fun logout(userId: String) {
        cacheService.del(CacheKeys.accessToken(userId))
        cacheService.del(CacheKeys.refreshToken(userId))
    }

    /**
     * 生成前端密码加密密钥（有效期 5 分钟）
     * @return 密钥（hex）与密钥 ID（ulid）
     */
    fun generateEncryptionKey(): EncryptionKey {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }.toHex()
        val keyId = IdGenerator.nextId()
        cacheService.setWithMilliseconds(
            CacheKeys.encryptionKey(keyId),
            key,
            TtlConstants.ENCRYPTION_KEY_TTL_MS,
        )
        return EncryptionKey(key, keyId)
    }

    /**
     * 获取加密密钥
     * @return 密钥，不存在返回 null
     */
    fun getEncryptionKey(keyId: String): String? = cacheService.get<String>(CacheKeys.encryptionKey(keyId))

    // ---- 内部实现 ----

    private fun createToken(userId: String, email: String, type: String): String {
        // 用 LinkedHashMap 保证字段顺序：userId, email, type, timestamp
        // 与 Node JSON.stringify 行为一致
        val payload = linkedMapOf<String, Any>(
            "userId" to userId,
            "email" to email,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
        )
        val json = objectMapper.writeValueAsString(payload)
        val tokenPart = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val signature = hmacSha256(tokenPart)
        return "$tokenPart.$signature"
    }

    private fun validateToken(
        token: String,
        expectedType: String,
        cacheKeyFn: (String) -> String,
    ): TokenPayload? {
        return try {
            val parts = token.split(".")
            if (parts.size != 2) return null
            val (tokenPart, signature) = parts

            // 验证签名
            val expectedSignature = hmacSha256(tokenPart)
            if (signature != expectedSignature) return null

            // 解析 payload
            val json = String(Base64.getDecoder().decode(tokenPart), Charsets.UTF_8)
            val payload = objectMapper.readValue(json, Map::class.java)
            if (payload["type"] != expectedType) return null

            val userId = payload["userId"] as? String ?: return null
            val email = payload["email"] as? String ?: return null

            // 单点令牌校验：Redis 中缓存的令牌必须与请求令牌相等
            val cachedToken = cacheService.get<String>(cacheKeyFn(userId))
            if (cachedToken != token) return null

            TokenPayload(userId, email)
        } catch (e: Exception) {
            null
        }
    }

    private fun hmacSha256(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(jwtSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** 令牌解析结果 */
data class TokenPayload(val userId: String, val email: String)

/** 令牌对 */
data class TokenPair(val accessToken: String, val refreshToken: String)

/** 加密密钥 */
data class EncryptionKey(val key: String, val keyId: String)
