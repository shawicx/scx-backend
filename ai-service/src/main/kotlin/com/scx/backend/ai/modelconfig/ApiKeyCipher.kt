package com.scx.backend.ai.modelconfig

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * @description API Key 加解密组件（AES-256-GCM）
 *
 * 密钥 = SHA-256(配置串)：优先取 ai.config-secret（AI_CONFIG_SECRET 环境变量），
 * 未配置时回退派生自 jwt.secret，保证本地开发零配置可用。
 * 存储格式：base64(iv):base64(cipher)；日志与响应永不输出明文/密钥材料。
 */
@Component
class ApiKeyCipher(
    @Value("\${ai.config-secret:}") configSecret: String,
    @Value("\${jwt.secret:default-secret}") jwtSecret: String,
) {

    private val key: SecretKey
    private val random = SecureRandom()

    init {
        val material = if (configSecret.isNotBlank()) configSecret else "ai-config:$jwtSecret"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        key = SecretKeySpec(digest, "AES")
    }

    /**
     * @description 加密 API Key 明文
     * @param plain API Key 明文
     * @return String 密文（base64(iv):base64(cipher)）
     *
     * @example encrypt("sk-abc123") == "AAAA:BBBB"
     */
    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return "${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(encrypted)}"
    }

    /**
     * @description 解密 API Key 密文
     * @param stored 密文（base64(iv):base64(cipher)）
     * @return String API Key 明文
     *
     * @example decrypt("AAAA:BBBB") == "sk-abc123"
     */
    fun decrypt(stored: String): String {
        val parts = stored.split(":")
        require(parts.size == 2) { "密文格式非法" }
        val iv = Base64.getDecoder().decode(parts[0])
        val encrypted = Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128

        /**
         * @description 生成 API Key 掩码（保留首尾少量字符，用于前端展示）
         * @param plain API Key 明文
         * @return String 掩码串（如 sk-…ab12），过短时统一为 ****
         */
        fun mask(plain: String): String =
            if (plain.length <= 8) "****" else "${plain.take(3)}…${plain.takeLast(4)}"
    }
}
