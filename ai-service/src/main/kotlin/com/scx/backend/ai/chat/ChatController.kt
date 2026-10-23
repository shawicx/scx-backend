package com.scx.backend.ai.chat

import com.scx.backend.ai.chat.dto.ChatRequestDto
import com.scx.backend.ai.chat.dto.MessageListResponseDto
import com.scx.backend.ai.security.AuthGuards
import com.scx.backend.ai.session.SessionService
import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description AI 对话控制器（多会话：会话内历史 / 流式发送）
 *
 * 路由前缀 /api/ai（context-path=/api）；chat 直写 SSE（void 返回，
 * 不经过 GlobalResponseHandler 包装），未配置模型时以 JSON 返回 9500。
 * 会话的列表/重命名/删除见 SessionController（/ai/sessions）。
 */
@Tag(name = "AI 对话", description = "会话内聊天：发送消息（SSE 流式）、历史消息与清空全部会话")
@RestController
@RequestMapping("/ai")
class ChatController(
    private val chatService: ChatService,
    private val sessionService: SessionService,
) {

    @Operation(summary = "会话历史消息", description = "指定会话最近 200 条消息，时间正序；归属校验失败返回 9002")
    @GetMapping("/messages", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun messages(
        @Parameter(description = "会话 ID", required = true) @RequestParam sessionId: String,
        request: HttpServletRequest,
    ): MessageListResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return chatService.getMessages(userId, sessionId)
    }

    @Operation(
        summary = "发送消息（SSE 流式）",
        description = "请求 JSON {sessionId?, content}；sessionId 缺省时新建会话（标题取消息前 30 字符）。" +
            "响应 text/event-stream：event: session 会话信息（最先发送）→ delta 增量 → done 完成（含 messageId/sessionId）→ error 失败；" +
            "未配置模型返回 9500，会话归属校验失败返回 9002",
    )
    @ApiResponse(
        responseCode = "200",
        description = "SSE 事件流（event: session/delta/done/error）",
        content = [Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = Schema(type = "string"))],
    )
    @PostMapping("/chat", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody dto: ChatRequestDto,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val userId = AuthGuards.requireUserId(request)
        chatService.chat(userId, dto.sessionId, dto.content, response)
    }

    @OperationLog(module = "AI 对话", action = "清空全部会话")
    @Operation(summary = "清空全部会话", description = "删除当前用户全部会话及会话内消息")
    @PostMapping("/messages/clear", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun clearAll(request: HttpServletRequest): MessageDto {
        val userId = AuthGuards.requireUserId(request)
        sessionService.clearAllSessions(userId)
        return MessageDto("全部会话已清空")
    }
}
