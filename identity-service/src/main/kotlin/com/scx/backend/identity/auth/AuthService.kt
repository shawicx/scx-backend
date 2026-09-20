package com.scx.backend.identity.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.constants.TtlConstants
import com.scx.backend.common.security.DataScope
import com.scx.backend.common.security.TokenPayload
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.identity.cache.CacheService
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
 *  token = base64(JSON({userId,email,type,timestamp,isAdmin,dataScope})) + "." + hexHmac
 *
 * 关键点：
 *  - base64 使用标准编码
 *  - JSON 字段顺序固定为 userId, email, type, timestamp, isAdmin（用 LinkedHashMap 保序）
 *  - HMAC-SHA256，密钥来自 JWT_SECRET 环境变量
 *  - 单点令牌：Redis 中缓存的令牌必须与请求令牌完全相等
 *  - isAdmin 嵌入令牌：签发时由调用方（UserService）计算，校验时回填到 TokenPayload，
 *    使网关/拦截器无需回查数据库即可判定管理员（Step 5 令牌嵌入角色改造）。
 *    旧令牌缺失该字段时默认 false（向后兼容）。
 *  - dataScope 嵌入令牌：数据权限范围（角色级，多角色取最宽），网关注入
 *    X-User-DataScope 头供下游行级过滤。旧令牌缺失该字段时默认 SELF。
 */
@Service
class AuthService(
    private val cacheService: CacheService,
    private val objectMapper: ObjectMapper,
    @Value("\${jwt.secret:default-secret}") private val jwtSecret: String,
) {
    /**
     * 生成访问令牌（有效期 2 小时）
     * @param isAdmin 是否为管理员，嵌入令牌 payload
     * @param dataScope 数据权限范围，嵌入令牌 payload（网关注入 X-User-DataScope）
     */
    fun generateAccessToken(userId: String, email: String, isAdmin: Boolean = false, dataScope: DataScope = DataScope.SELF): String {
        val token = createToken(userId, email, "access", isAdmin, dataScope)
        cacheService.setWithMilliseconds(
            CacheKeys.accessToken(userId),
            token,
            TtlConstants.ACCESS_TOKEN_TTL_MS,
        )
        return token
    }

    /**
     * 生成刷新令牌（有效期 7 天）
     * @param isAdmin 是否为管理员，嵌入令牌 payload
     * @param dataScope 数据权限范围，嵌入令牌 payload
     */
    fun generateRefreshToken(userId: String, email: String, isAdmin: Boolean = false, dataScope: DataScope = DataScope.SELF): String {
        val token = createToken(userId, email, "refresh", isAdmin, dataScope)
        cacheService.setWithMilliseconds(
            CacheKeys.refreshToken(userId),
            token,
            TtlConstants.REFRESH_TOKEN_TTL_MS,
        )
        return token
    }

    /**
     * 验证访问令牌
     * @return 用户信息（含 isAdmin），验证失败返回 null
     */
    fun validateAccessToken(token: String): TokenPayload? = validateToken(token, "access", CacheKeys::accessToken)

    /**
     * 验证刷新令牌
     * @return 用户信息（含 isAdmin），验证失败返回 null
     */
    fun validateRefreshToken(token: String): TokenPayload? = validateToken(token, "refresh", CacheKeys::refreshToken)

    /**
     * 刷新令牌对
     *
     * 刷新时重新计算 isAdmin 与 dataScope（角色变更后刷新令牌即生效），保证嵌入令牌的标志最新。
     * @param isAdminProvider 根据用户 ID 计算 isAdmin 的回调（由 UserService 注入，避免 AuthService 反向依赖 UserService）
     * @param dataScopeProvider 根据用户 ID 计算数据范围的回调（同上）
     * @return 新的 accessToken + refreshToken，验证失败返回 null
     */
    fun refreshTokens(
        refreshToken: String,
        isAdminProvider: ((String) -> Boolean)? = null,
        dataScopeProvider: ((String) -> DataScope)? = null,
    ): TokenPair? {
        val userInfo = validateRefreshToken(refreshToken) ?: return null
        // 刷新时重算 isAdmin / dataScope：若调用方提供回调则用最新值，否则沿用旧令牌中的值
        val isAdmin = isAdminProvider?.invoke(userInfo.userId) ?: userInfo.isAdmin
        val dataScope = dataScopeProvider?.invoke(userInfo.userId) ?: userInfo.dataScope
        return TokenPair(
            accessToken = generateAccessToken(userInfo.userId, userInfo.email, isAdmin, dataScope),
            refreshToken = generateRefreshToken(userInfo.userId, userInfo.email, isAdmin, dataScope),
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

    private fun createToken(userId: String, email: String, type: String, isAdmin: Boolean, dataScope: DataScope): String {
        // 用 LinkedHashMap 保证字段顺序：userId, email, type, timestamp, isAdmin, dataScope
        // 与 Node JSON.stringify 行为一致（前 4 字段）；isAdmin / dataScope 为新增字段
        val payload = linkedMapOf<String, Any>(
            "userId" to userId,
            "email" to email,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "isAdmin" to isAdmin,
            "dataScope" to dataScope.name,
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
            // isAdmin 为新增字段，旧令牌缺失时默认 false（向后兼容）
            val isAdmin = (payload["isAdmin"] as? Boolean) ?: false
            // dataScope 为新增字段，旧令牌缺失时默认 SELF（最小数据权限）
            val dataScope = DataScope.fromName(payload["dataScope"] as? String)

            // 单点令牌校验：Redis 中缓存的令牌必须与请求令牌相等
            val cachedToken = cacheService.get<String>(cacheKeyFn(userId))
            if (cachedToken != token) return null

            TokenPayload(userId, email, isAdmin, dataScope)
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

/** 令牌解析结果（含 isAdmin） */
data class TokenPayload(val userId: String, val email: String, val isAdmin: Boolean = false)

/** 令牌对 */
data class TokenPair(val accessToken: String, val refreshToken: String)

/** 加密密钥 */
data class EncryptionKey(val key: String, val keyId: String)
