package com.scx.backend.blog.support

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.Deque
import java.util.LinkedList

/**
 * @description 博客匿名互动内存限流器（滑动窗口，零依赖的尽力而为防护）
 *
 * 公开评论/点赞/阅读接口没有登录态可依托，用"标识 + 时间窗"做基础频率
 * 限制；单实例内存实现，重启即清零，仅作为防滥用第一道闸门，不承诺精确。
 */
@Component
class BlogRateLimiter {

    private val windows = ConcurrentHashMap<String, Deque<Long>>()

    /**
     * @description 判断标识在窗口内是否超过次数上限，未超则记账放行
     * @param key 限流标识（如 ipHash + 动作）
     * @param limit 窗口内允许的最大次数
     * @param windowMillis 窗口长度（毫秒）
     * @return Boolean true 放行 / false 已超限
     */
    fun tryAcquire(key: String, limit: Int, windowMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val deque = windows.computeIfAbsent(key) { LinkedList() }
        synchronized(deque) {
            while (deque.isNotEmpty() && now - deque.first() > windowMillis) {
                deque.removeFirst()
            }
            if (deque.size >= limit) return false
            deque.addLast(now)
            return true
        }
    }

    /**
     * @description 清理全部过期窗口（防止标识无上限堆积；由调用方定期触发）
     * @param windowMillis 最长窗口（毫秒），早于该时间无记账的标识整条移除
     */
    fun cleanup(windowMillis: Long) {
        val now = System.currentTimeMillis()
        windows.entries.removeIf { (_, deque) ->
            synchronized(deque) { deque.isEmpty() || now - deque.last() > windowMillis }
        }
    }
}
