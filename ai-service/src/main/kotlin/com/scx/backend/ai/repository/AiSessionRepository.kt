package com.scx.backend.ai.repository

import com.scx.backend.ai.entity.AiSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * @description AI 会话仓库（多会话：用户维度列表与归属校验）
 */
interface AiSessionRepository : JpaRepository<AiSession, String> {

    /**
     * @description 取用户最近 limit 条会话（按最后活跃时间倒序）
     * @param userId 用户 ID
     * @return List<AiSession> 按 updatedAt 倒序的会话列表（上限 100）
     */
    fun findTop100ByUserIdOrderByUpdatedAtDesc(userId: String): List<AiSession>

    /**
     * @description 按 ID + 用户 ID 查会话（归属校验：他人会话视同不存在）
     * @param id 会话 ID
     * @param userId 用户 ID
     * @return AiSession? 归属当前用户的会话
     */
    fun findByIdAndUserId(id: String, userId: String): AiSession?

    /**
     * @description 删除用户全部会话（清空全部会话时调用；消息由服务层先行删除）
     * @param userId 用户 ID
     */
    @Transactional
    fun deleteByUserId(userId: String)
}
