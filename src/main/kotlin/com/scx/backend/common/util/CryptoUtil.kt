package com.scx.backend.common.util

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * 加密工具
 *
 * AES-256-CTR 模式，格式 `ivHex:encryptedHex`，与 Node.js crypto.createCipheriv('aes-256-ctr') 互通。
 * 密钥为 32 字节 hex（64 字符）；IV 为 16 字节 hex（32 字符）。
 */
object CryptoUtil {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CTR/NoPadding"
    private const val IV_LENGTH = 16
    private const val KEY_LENGTH = 32
    private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")

    /**
     * 生成随机密钥
     * @return 32 字节随机密钥（hex 格式，64 字符）
     */
    fun generateKey(): String {
        val key = ByteArray(KEY_LENGTH)
        SecureRandom().nextBytes(key)
        return key.toHex()
    }

    /**
     * 加密文本
     * @param text 要加密的明文
     * @param key 加密密钥（hex 格式，64 字符）
     * @return 加密后的文本，格式 `ivHex:encryptedHex`
     */
    fun encrypt(text: String, key: String): String {
        try {
            val keyBuffer = key.fromHex()
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBuffer, ALGORITHM), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            return "${iv.toHex()}:${encrypted.toHex()}"
        } catch (e: Exception) {
            throw RuntimeException("加密失败", e)
        }
    }

    /**
     * 解密文本
     * @param encryptedText 加密文本，格式 `ivHex:encryptedHex`
     * @param key 解密密钥（hex 格式，64 字符）
     * @return 解密后的明文
     */
    fun decrypt(encryptedText: String, key: String): String {
        try {
            val parts = encryptedText.split(":")
            require(parts.size == 2) { "加密数据格式错误" }

            val (ivHex, encryptedHex) = parts
            val keyBuffer = key.fromHex()
            val ivBuffer = ivHex.fromHex()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBuffer, ALGORITHM), IvParameterSpec(ivBuffer))
            val decrypted = cipher.doFinal(encryptedHex.fromHex())
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("解密失败", e)
        }
    }

    /**
     * 验证加密数据完整性
     * @param encryptedText 待校验文本
     * @return 是否为有效的加密格式（ivHex:encryptedHex 且 iv 长度为 32 hex）
     */
    fun isValidEncryptedFormat(encryptedText: String?): Boolean {
        if (encryptedText.isNullOrEmpty()) return false
        val parts = encryptedText.split(":")
        if (parts.size != 2) return false
        val (ivHex, encrypted) = parts
        return HEX_PATTERN.matches(ivHex) &&
            HEX_PATTERN.matches(encrypted) &&
            ivHex.length == IV_LENGTH * 2
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "无效的 hex 字符串" }
        return ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
    }
}
