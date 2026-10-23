package com.scx.backend.ai.repository

import com.scx.backend.ai.entity.AiMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * @description AI 会话消息仓库（多会话：按 sessionId 维度查询/清理）
 */
interface AiMessageRepository : JpaRepository<AiMessage, String> {

    /**
     * @description 取会话最近 200 条消息（倒序），调用方自行反转成时间正序
     * @param sessionId 会话 ID
     * @return List<AiMessage> 按时间倒序的消息列表
     */
    fun findTop200BySessionIdOrderByCreatedAtDesc(sessionId: String): List<AiMessage>

    /**
     * @description 取会话最近 20 条消息（倒序），作为发送给模型的上下文窗口
     * @param sessionId 会话 ID
     * @return List<AiMessage> 按时间倒序的消息列表
     */
    fun findTop20BySessionIdOrderByCreatedAtDesc(sessionId: String): List<AiMessage>

    /**
     * @description 删除会话全部消息（服务层删除会话时显式调用，不依赖数据库级联）
     * @param sessionId 会话 ID
     */
    @Transactional
    fun deleteBySessionId(sessionId: String)

    /**
     * @description 删除用户全部消息（清空全部会话时调用）
     * @param userId 用户 ID
     */
    @Transactional
    fun deleteByUserId(userId: String)
}
