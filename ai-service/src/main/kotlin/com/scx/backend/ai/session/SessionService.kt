package com.scx.backend.ai.session

import com.scx.backend.ai.entity.AiSession
import com.scx.backend.ai.repository.AiMessageRepository
import com.scx.backend.ai.repository.AiSessionRepository
import com.scx.backend.ai.session.dto.SessionListResponseDto
import com.scx.backend.ai.session.dto.SessionResponseDto
import com.scx.backend.ai.session.dto.UpdateSessionDto
import com.scx.backend.common.exception.SystemException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @description AI 会话服务（列表 / 重命名 / 删除；归属校验统一走 findOwned）
 *
 * 删除会话时显式先删消息再删会话（不依赖数据库级联，H2 测试环境同样成立）。
 */
@Service
class SessionService(
    private val aiSessionRepository: AiSessionRepository,
    private val aiMessageRepository: AiMessageRepository,
) {

    /**
     * @description 查询用户会话列表（按最后活跃时间倒序，最多 100 条）
     * @param userId 用户 ID
     * @return SessionListResponseDto 会话列表
     */
    fun listSessions(userId: String): SessionListResponseDto =
        SessionListResponseDto(
            aiSessionRepository.findTop100ByUserIdOrderByUpdatedAtDesc(userId)
                .map { SessionResponseDto.from(it) },
        )

    /**
     * @description 重命名会话（仅标题可改）
     * @param userId 用户 ID
     * @param dto 重命名请求
     * @return SessionResponseDto 更新后的会话
     */
    @Transactional
    fun renameSession(userId: String, dto: UpdateSessionDto): SessionResponseDto {
        val session = findOwned(userId, dto.id)
        session.title = dto.title.trim()
        return SessionResponseDto.from(aiSessionRepository.save(session))
    }

    /**
     * @description 删除会话（连同会话内全部消息）
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     */
    @Transactional
    fun deleteSession(userId: String, sessionId: String) {
        val session = findOwned(userId, sessionId)
        aiMessageRepository.deleteBySessionId(session.id)
        aiSessionRepository.delete(session)
    }

    /**
     * @description 清空全部会话（删除该用户所有会话及消息）
     * @param userId 用户 ID
     */
    @Transactional
    fun clearAllSessions(userId: String) {
        aiMessageRepository.deleteByUserId(userId)
        aiSessionRepository.deleteByUserId(userId)
    }

    /**
     * @description 归属校验：会话不存在或不属于当前用户时抛 9002
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return AiSession 归属当前用户的会话
     */
    fun findOwned(userId: String, sessionId: String): AiSession =
        aiSessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw SystemException.dataNotFound("会话不存在或已删除")
}
