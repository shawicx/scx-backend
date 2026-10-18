package com.scx.backend.ai.repository

import com.scx.backend.ai.entity.AiModelConfig
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @description AI 模型配置仓库（每用户一条，userId 唯一）
 */
interface AiModelConfigRepository : JpaRepository<AiModelConfig, String> {

    /**
     * @description 按用户 ID 查询配置
     * @param userId 用户 ID
     * @return AiModelConfig? 配置（未配置时为 null）
     */
    fun findByUserId(userId: String): AiModelConfig?
}
