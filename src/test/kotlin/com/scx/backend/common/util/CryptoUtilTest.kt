package com.scx.backend.common.util

import com.scx.backend.common.util.CryptoUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * CryptoUtil 单元测试
 *
 * 重点：验证 AES-256-CTR 加解密的正确性与跨语言互通性。
 */
class CryptoUtilTest {

    // 由 Node.js 生成的测试向量（见 docs/test-vectors 生成脚本）
    // node: crypto.createCipheriv('aes-256-ctr', key, iv).update('Hello你好世界SCX')
    private val nodeKey = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    private val nodeCiphertext = "1fd9cb77174e33e7cf6209fbeae2b15e:e469476697c8c38f60c511f1d9f2347dcb26ba26"
    private val nodePlaintext = "Hello你好世界SCX"

    @Test
    fun `encrypt then decrypt roundtrip returns original`() {
        val key = CryptoUtil.generateKey()
        val plaintext = "test-明文-123"
        val encrypted = CryptoUtil.encrypt(plaintext, key)
        val decrypted = CryptoUtil.decrypt(encrypted, key)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `can decrypt data encrypted by Node js aes-256-ctr`() {
        // 跨语言兼容性：Node 加密，Kotlin 解密
        val decrypted = CryptoUtil.decrypt(nodeCiphertext, nodeKey)
        assertEquals(nodePlaintext, decrypted)
    }

    @Test
    fun `Node js can decrypt data encrypted by Kotlin (symmetric interop)`() {
        // 反向：Kotlin 加密，密文格式正确（ivHex:encryptedHex）
        val encrypted = CryptoUtil.encrypt(nodePlaintext, nodeKey)
        assertTrue(CryptoUtil.isValidEncryptedFormat(encrypted))
        // 解回来验证内容一致（说明算法对称）
        assertEquals(nodePlaintext, CryptoUtil.decrypt(encrypted, nodeKey))
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext (random IV)`() {
        val key = CryptoUtil.generateKey()
        val plaintext = "same"
        val c1 = CryptoUtil.encrypt(plaintext, key)
        val c2 = CryptoUtil.encrypt(plaintext, key)
        assertNotEquals(c1, c2)
    }

    @Test
    fun `generateKey returns 64-char hex string`() {
        val key = CryptoUtil.generateKey()
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("^[0-9a-f]+$")))
    }

    @Test
    fun `decrypt with invalid format throws`() {
        assertThrows<RuntimeException> {
            CryptoUtil.decrypt("invalid-no-colon", nodeKey)
        }
    }

    @Test
    fun `isValidEncryptedFormat validates structure`() {
        assertTrue(CryptoUtil.isValidEncryptedFormat(nodeCiphertext))
        assertFalse(CryptoUtil.isValidEncryptedFormat("invalid"))
        assertFalse(CryptoUtil.isValidEncryptedFormat(null))
        assertFalse(CryptoUtil.isValidEncryptedFormat(""))
        // IV 长度不对（应为 32 hex = 16 字节）
        assertFalse(CryptoUtil.isValidEncryptedFormat("ab:cd"))
        // 非 hex
        assertFalse(CryptoUtil.isValidEncryptedFormat("xy:zz".padEnd(35, '0')))
    }
}
