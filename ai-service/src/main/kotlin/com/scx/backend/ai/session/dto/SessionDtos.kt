package com.scx.backend.ai.session.dto

import com.scx.backend.ai.entity.AiSession
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * @description AI 会话相关 DTO（列表 / 重命名）
 */

/** 会话响应 */
@Schema(description = "会话响应")
data class SessionResponseDto(
    @Schema(description = "会话 ID") val id: String,
    @Schema(description = "会话标题") val title: String,
    @Schema(description = "创建时间") val createdAt: LocalDateTime,
    @Schema(description = "最后活跃时间") val updatedAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 实体转响应 DTO
         * @param entity 会话实体
         * @return SessionResponseDto 会话响应
         */
        fun from(entity: AiSession): SessionResponseDto = SessionResponseDto(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

/** 会话列表响应（按最后活跃时间倒序） */
@Schema(description = "会话列表响应")
data class SessionListResponseDto(
    @Schema(description = "会话列表") val list: List<SessionResponseDto>,
)

/** 重命名会话请求 */
@Schema(description = "重命名会话请求")
data class UpdateSessionDto(
    @Schema(description = "会话 ID", required = true)
    @field:NotBlank(message = "会话ID不能为空")
    @field:Size(max = 30, message = "会话ID不能超过30个字符")
    val id: String,

    @Schema(description = "新标题", required = true)
    @field:NotBlank(message = "会话标题不能为空")
    @field:Size(max = 50, message = "会话标题不能超过50个字符")
    val title: String,
)
