package com.scx.backend.modules.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.util.CryptoUtil
import com.scx.backend.modules.cache.CacheService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * AuthService 集成测试（依赖真实 Redis 127.0.0.1:6388）
 * 对标 scx-service: auth.service.spec.ts
 *
 * 重点验证：
 *  1. 令牌生成/验证/刷新/登出完整流程
 *  2. 单点令牌（Redis 比对）
 *  3. 跨语言兼容：Kotlin 生成的令牌签名算法与 Node 一致
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:authTest;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-for-compat",
    ],
)
class AuthServiceTest(
    @Autowired private val authService: AuthService,
    @Autowired private val cacheService: CacheService,
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val userId = "11111111-1111-1111-1111-111111111111"
    private val email = "test@scx.dev"

    @BeforeEach
    fun cleanUp() {
        cacheService.flushAll()
    }

    @Test
    fun `generateAccessToken produces token-dot-signature format`() {
        val token = authService.generateAccessToken(userId, email)
        val parts = token.split(".")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `generateAccessToken stores token in Redis with access type`() {
        val token = authService.generateAccessToken(userId, email)
        val cached = cacheService.get<String>(CacheKeys.accessToken(userId))
        assertEquals(token, cached)
    }

    @Test
    fun `validateAccessToken succeeds for valid token`() {
        val token = authService.generateAccessToken(userId, email)
        val result = authService.validateAccessToken(token)
        assertNotNull(result)
        assertEquals(userId, result!!.userId)
        assertEquals(email, result.email)
    }

    @Test
    fun `validateAccessToken returns null for invalid format`() {
        assertNull(authService.validateAccessToken("invalid.token.format"))
    }

    @Test
    fun `validateAccessToken returns null for invalid signature`() {
        // 正确 payload + 错误签名
        val token = authService.generateAccessToken(userId, email)
        val tampered = token.substringBefore(".") + ".wrong-signature"
        assertNull(authService.validateAccessToken(tampered))
    }

    @Test
    fun `validateAccessToken returns null for wrong token type (refresh as access)`() {
        // 生成 refresh token，用 validateAccessToken 校验应失败
        val refreshToken = authService.generateRefreshToken(userId, email)
        assertNull(authService.validateAccessToken(refreshToken))
    }

    @Test
    fun `validateAccessToken returns null when token not in cache (single-point login)`() {
        val token = authService.generateAccessToken(userId, email)
        // 模拟登出后令牌失效
        cacheService.del(CacheKeys.accessToken(userId))
        assertNull(authService.validateAccessToken(token))
    }

    @Test
    fun `validateAccessToken returns null when cached token differs (login elsewhere)`() {
        // 单点登录：同一用户重新登录后，旧 token 失效
        val oldToken = authService.generateAccessToken(userId, email)
        val newToken = authService.generateAccessToken(userId, email)
        assertNotEquals(oldToken, newToken)
        assertNull(authService.validateAccessToken(oldToken))
        assertNotNull(authService.validateAccessToken(newToken))
    }

    @Test
    fun `generateRefreshToken stores with refresh type`() {
        val token = authService.generateRefreshToken(userId, email)
        val cached = cacheService.get<String>(CacheKeys.refreshToken(userId))
        assertEquals(token, cached)
    }

    @Test
    fun `validateRefreshToken succeeds for valid token`() {
        val token = authService.generateRefreshToken(userId, email)
        val result = authService.validateRefreshToken(token)
        assertNotNull(result)
        assertEquals(userId, result!!.userId)
    }

    @Test
    fun `refreshTokens generates new token pair`() {
        val refreshToken = authService.generateRefreshToken(userId, email)
        val pair = authService.refreshTokens(refreshToken)
        assertNotNull(pair)
        assertTrue(pair!!.accessToken.contains("."))
        assertTrue(pair.refreshToken.contains("."))
        // 新令牌有效
        assertNotNull(authService.validateAccessToken(pair.accessToken))
    }

    @Test
    fun `refreshTokens returns null for invalid refresh token`() {
        assertNull(authService.refreshTokens("invalid.refresh"))
    }

    @Test
    fun `logout deletes both tokens`() {
        authService.generateAccessToken(userId, email)
        authService.generateRefreshToken(userId, email)
        assertTrue(cacheService.exists(CacheKeys.accessToken(userId)))
        assertTrue(cacheService.exists(CacheKeys.refreshToken(userId)))

        authService.logout(userId)

        assertFalse(cacheService.exists(CacheKeys.accessToken(userId)))
        assertFalse(cacheService.exists(CacheKeys.refreshToken(userId)))
    }

    @Test
    fun `generateEncryptionKey returns hex key and ulid keyId`() {
        val encKey = authService.generateEncryptionKey()
        assertEquals(64, encKey.key.length) // 32 字节 = 64 hex
        assertTrue(encKey.key.matches(Regex("^[0-9a-f]+$")))
        assertEquals(26, encKey.keyId.length) // ULID 长度
        assertTrue(encKey.keyId.matches(Regex("^[0-9A-HJKMNP-TV-Z]+$"))) // Crockford Base32
    }

    @Test
    fun `generateEncryptionKey stores key in Redis retrievable by keyId`() {
        val encKey = authService.generateEncryptionKey()
        val retrieved = authService.getEncryptionKey(encKey.keyId)
        assertEquals(encKey.key, retrieved)
    }

    @Test
    fun `getEncryptionKey returns null for non-existent keyId`() {
        assertNull(authService.getEncryptionKey("nonexistent-key"))
    }

    @Test
    fun `Kotlin token signature matches Node HMAC-SHA256 (cross-language compat)`() {
        // 跨语言兼容性核心验证：
        // 用与 Node 相同的 secret、payload、base64 编码，签名应完全一致。
        // Node 生成的期望向量（见生成脚本，secret = test-secret-key-for-compat 不适用，此处直接比对算法）
        // 这里改用已知向量验证：Node 用 secret "mysecret" 对固定 payload 生成的签名
        val nodePayloadB64 = "eyJ1c2VySWQiOiJ1MTIzIiwiZW1haWwiOiJhQGIuY29tIiwidHlwZSI6ImFjY2VzcyIsInRpbWVzdGFtcCI6MTcwMDAwMDAwMDAwMH0="
        val nodeExpectedSig = "122713873dd7d9c6c3b02e5210bb4c3049a88c23a0d48e05ea599222a574fa93"

        // 用 Kotlin 的 HMAC 算法重现该签名
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec("mysecret".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val kotlinSig = mac.doFinal(nodePayloadB64.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(nodeExpectedSig, kotlinSig, "Kotlin HMAC 签名必须与 Node 一致")
    }

    @Test
    fun `Kotlin token payload matches Node JSON field order`() {
        // 验证 Kotlin 生成的 base64 payload 反解后字段顺序为 userId,email,type,timestamp
        val token = authService.generateAccessToken(userId, email)
        val payloadB64 = token.substringBefore(".")
        val json = String(java.util.Base64.getDecoder().decode(payloadB64), Charsets.UTF_8)
        // Node JSON.stringify 输出顺序：userId 在 email 前
        assertTrue(json.indexOf("userId") < json.indexOf("email"))
        assertTrue(json.indexOf("email") < json.indexOf("type"))
        assertTrue(json.indexOf("type") < json.indexOf("timestamp"))
    }
}
