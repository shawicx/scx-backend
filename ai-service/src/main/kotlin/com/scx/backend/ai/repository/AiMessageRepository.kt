package com.scx.backend.ai.repository

import com.scx.backend.ai.entity.AiMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * @description AI 会话消息仓库（单会话：按 userId 维度查询/清理）
 */
interface AiMessageRepository : JpaRepository<AiMessage, String> {

    /**
     * @description 取用户最近 limit 条消息（倒序），调用方自行反转成时间正序
     * @param userId 用户 ID
     * @param limit 取最近多少条（方法名中的 Top 数字为派生上限）
     * @return List<AiMessage> 按时间倒序的消息列表
     */
    fun findTop200ByUserIdOrderByCreatedAtDesc(userId: String): List<AiMessage>

    /**
     * @description 取用户最近 20 条消息（倒序），作为发送给模型的上下文窗口
     * @param userId 用户 ID
     * @return List<AiMessage> 按时间倒序的消息列表
     */
    fun findTop20ByUserIdOrderByCreatedAtDesc(userId: String): List<AiMessage>

    /**
     * @description 清空用户会话（初版单会话即全删）
     * @param userId 用户 ID
     */
    @Transactional
    fun deleteByUserId(userId: String)
}
