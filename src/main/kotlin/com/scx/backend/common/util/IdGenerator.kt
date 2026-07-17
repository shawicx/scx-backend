package com.scx.backend.common.util

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * ID 生成器（ULID）
 *
 * 生成 26 字符 Crockford Base32 编码的 ULID（与 Node ulid 库格式一致）。
 * 全项目统一使用 ULID 作为实体主键。
 *
 * 结构：时间戳(10 字符) + 随机(16 字符)
 */
@Component
object IdGenerator {
    private val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val random = SecureRandom()

    fun nextId(): String {
        val timestamp = System.currentTimeMillis()
        val randomBytes = ByteArray(10).also { random.nextBytes(it) }

        val sb = StringBuilder(26)
        // 时间戳部分（10 字符，48 位）
        var ts = timestamp
        val tsChars = CharArray(10)
        for (i in 9 downTo 0) {
            tsChars[i] = ENCODING[(ts and 0x1F).toInt()]
            ts = ts shr 5
        }
        sb.append(tsChars)
        // 随机部分（16 字符）
        for (i in 0 until 16) {
            sb.append(ENCODING[(randomBytes[i % 10].toInt() and 0xFF) % 32])
        }
        return sb.toString()
    }
}
