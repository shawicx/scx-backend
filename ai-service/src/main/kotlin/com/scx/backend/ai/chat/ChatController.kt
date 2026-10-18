package com.scx.backend.ai.chat

import com.scx.backend.ai.chat.dto.ChatRequestDto
import com.scx.backend.ai.chat.dto.MessageListResponseDto
import com.scx.backend.ai.security.AuthGuards
import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description AI 对话控制器（单会话：历史 / 清空 / 流式发送）
 *
 * 路由前缀 /api/ai（context-path=/api）；chat 直写 SSE（void 返回，
 * 不经过 GlobalResponseHandler 包装），未配置模型时以 JSON 返回 9500。
 */
@Tag(name = "AI 对话", description = "单会话聊天：发送消息（SSE 流式）、历史消息、清空会话")
@RestController
@RequestMapping("/ai")
class ChatController(
    private val chatService: ChatService,
) {

    @Operation(summary = "历史消息", description = "当前用户最近 200 条消息，时间正序")
    @GetMapping("/messages", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun messages(request: HttpServletRequest): MessageListResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return chatService.getMessages(userId)
    }

    @OperationLog(module = "AI 对话", action = "清空会话")
    @Operation(summary = "清空会话", description = "删除当前用户全部消息（初版单会话）")
    @PostMapping("/messages/clear", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun clear(request: HttpServletRequest): MessageDto {
        val userId = AuthGuards.requireUserId(request)
        chatService.clearMessages(userId)
        return MessageDto("会话已清空")
    }

    @Operation(
        summary = "发送消息（SSE 流式）",
        description = "请求 JSON {content}；响应 text/event-stream：event: delta 增量 / done 完成（含 messageId）/ error 失败；未配置模型返回 9500",
    )
    @PostMapping("/chat", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody dto: ChatRequestDto,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val userId = AuthGuards.requireUserId(request)
        chatService.chat(userId, dto.content, response)
    }
}
