package com.scx.backend.ai.chat.dto

import com.scx.backend.ai.entity.AiMessage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * @description AI 对话相关 DTO（发送消息 / 历史消息 / SSE 事件负载）
 */

/** 发送消息请求（sessionId 缺省时服务端新建会话，标题取首条消息前 30 字符） */
@Schema(description = "发送消息请求")
data class ChatRequestDto(
    @Schema(description = "会话 ID（缺省时新建会话）")
    @field:Size(max = 30, message = "会话ID不能超过30个字符")
    val sessionId: String? = null,

    @Schema(description = "用户消息内容", required = true)
    @field:NotBlank(message = "消息内容不能为空")
    @field:Size(max = 8000, message = "消息内容不能超过8000个字符")
    val content: String,
)

/** 会话消息响应 */
@Schema(description = "会话消息响应")
data class MessageResponseDto(
    @Schema(description = "消息 ID") val id: String,
    @Schema(description = "角色 user/assistant") val role: String,
    @Schema(description = "消息内容") val content: String,
    @Schema(description = "创建时间") val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 实体转响应 DTO
         * @param entity 消息实体
         * @return MessageResponseDto 消息响应
         */
        fun from(entity: AiMessage): MessageResponseDto = MessageResponseDto(
            id = entity.id,
            role = entity.role,
            content = entity.content,
            createdAt = entity.createdAt,
        )
    }
}

/** 历史消息响应（时间正序，最近 200 条） */
@Schema(description = "历史消息响应")
data class MessageListResponseDto(
    @Schema(description = "消息列表（时间正序）") val list: List<MessageResponseDto>,
)

/** SSE 会话事件负载（流式开始前发送：新建或既有会话的 ID 与标题） */
@Schema(description = "SSE 会话事件（event: session）")
data class SessionEventDto(
    @Schema(description = "会话 ID") val sessionId: String,
    @Schema(description = "会话标题") val title: String,
)

/** SSE 增量事件负载 */
@Schema(description = "SSE 增量事件（event: delta）")
data class DeltaEventDto(
    @Schema(description = "增量文本") val content: String,
)

/** SSE 完成事件负载 */
@Schema(description = "SSE 完成事件（event: done）")
data class DoneEventDto(
    @Schema(description = "落库后的助手消息 ID") val messageId: String,
    @Schema(description = "会话 ID") val sessionId: String,
)

/** SSE 失败事件负载 */
@Schema(description = "SSE 失败事件（event: error）")
data class ErrorEventDto(
    @Schema(description = "失败原因") val message: String,
)
