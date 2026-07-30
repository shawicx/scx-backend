package com.scx.backend.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * @description 令牌编解码工具（自研令牌协议，非标准 JWT）
 *
 * 协议格式：`token = base64(JSON(payload)) + "." + hexHmac(tokenPart)`
 *  - payload 字段顺序固定：userId, email, type, timestamp, isAdmin（用 LinkedHashMap 保序）
 *  - 签名算法 HMAC-SHA256，密钥来自 JWT_SECRET
 *
 * 本类是无状态工具，供网关（验签）与 identity 服务（签发/校验）共用，
 * 保证两端令牌协议一致。本类只负责编解码与签名比对，不涉及 Redis 单点令牌校验
 * （后者由各调用方自行处理）。
 *
 * @param objectMapper JSON 序列化器（由各服务注入，复用其配置）
 * @param secret HMAC 密钥
 */
class TokenCodec(
    private val objectMapper: ObjectMapper,
    private val secret: String,
) {
    /**
     * @description 构造令牌的 payload（固定字段顺序）
     * @param userId 用户 ID
     * @param email 用户邮箱
     * @param type 令牌类型（access / refresh）
     * @param isAdmin 是否为管理员
     * @return 序列化后的 payload（userId, email, type, timestamp, isAdmin）
     *
     * @example val payload = codec.buildPayload("01...", "a@b.com", "access", false)
     */
    fun buildPayload(userId: String, email: String, type: String, isAdmin: Boolean): Map<String, Any> =
        // 用 LinkedHashMap 保证字段顺序，与历史令牌格式一致
        linkedMapOf<String, Any>(
            "userId" to userId,
            "email" to email,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "isAdmin" to isAdmin,
        )

    /**
     * @description 签发令牌（base64(payload) + "." + hexHmac）
     * @param payload 令牌 payload（由 [buildPayload] 构造）
     * @return 完整令牌字符串
     *
     * @example val token = codec.encode(codec.buildPayload(id, email, "access", false))
     */
    fun encode(payload: Map<String, Any>): String {
        val json = objectMapper.writeValueAsString(payload)
        val tokenPart = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val signature = hmacSha256(tokenPart)
        return "$tokenPart.$signature"
    }

    /**
     * @description 验证令牌签名并解析 payload
     *
     * 仅校验签名格式与可解析性，不校验类型与单点令牌。类型校验与 Redis 单点令牌校验
     * 由调用方（AuthService / 网关）按需处理。旧令牌缺失 isAdmin 字段时默认 false。
     *
     * @param token 待校验令牌
     * @return 解析成功返回 [TokenPayload]，签名错误或格式异常返回 null
     *
     * @example val payload: TokenPayload? = codec.decode(token)
     */
    fun decode(token: String): TokenPayload? {
        return try {
            val parts = token.split(".")
            if (parts.size != 2) return null
            val (tokenPart, signature) = parts

            // 验证签名
            if (signature != hmacSha256(tokenPart)) return null

            // 解析 payload
            val json = String(Base64.getDecoder().decode(tokenPart), Charsets.UTF_8)
            val payload = objectMapper.readValue(json, Map::class.java)

            val userId = payload["userId"] as? String ?: return null
            val email = payload["email"] as? String ?: return null
            // isAdmin 为新增字段，旧令牌缺失时默认 false（向后兼容）
            val isAdmin = (payload["isAdmin"] as? Boolean) ?: false
            // type 与 timestamp 不在此校验，留给调用方按类型处理
            TokenPayload(userId, email, isAdmin)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * @description 解析令牌的 type 字段（access / refresh），不验签
     * @param token 令牌
     * @return type 字段值，解析失败返回 null
     */
    fun extractType(token: String): String? {
        return try {
            val tokenPart = token.split(".").firstOrNull() ?: return null
            val json = String(Base64.getDecoder().decode(tokenPart), Charsets.UTF_8)
            objectMapper.readValue(json, Map::class.java)["type"] as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun hmacSha256(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
