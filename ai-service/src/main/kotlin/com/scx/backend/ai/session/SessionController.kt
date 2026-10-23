package com.scx.backend.ai.session

import com.scx.backend.ai.security.AuthGuards
import com.scx.backend.ai.session.dto.SessionListResponseDto
import com.scx.backend.ai.session.dto.SessionResponseDto
import com.scx.backend.ai.session.dto.UpdateSessionDto
import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description AI 会话控制器（多会话管理）
 *
 * 路由前缀 /api/ai/sessions（context-path=/api）；身份经网关注入的 X-User-* 头解析。
 */
@Tag(name = "AI 会话", description = "多会话管理：列表、重命名、删除（删除连同会话内消息）")
@RestController
@RequestMapping("/ai/sessions", produces = [MediaType.APPLICATION_JSON_VALUE])
class SessionController(
    private val sessionService: SessionService,
) {

    @Operation(summary = "会话列表", description = "当前用户会话，按最后活跃时间倒序，最多 100 条")
    @GetMapping("/list")
    fun list(request: HttpServletRequest): SessionListResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return sessionService.listSessions(userId)
    }

    @OperationLog(module = "AI 会话", action = "重命名会话")
    @Operation(summary = "重命名会话", description = "仅标题可改，归属校验失败返回 9002")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateSessionDto, request: HttpServletRequest): SessionResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return sessionService.renameSession(userId, dto)
    }

    @OperationLog(module = "AI 会话", action = "删除会话")
    @Operation(summary = "删除会话", description = "连同会话内全部消息一并删除")
    @DeleteMapping("/delete")
    fun delete(
        @Parameter(description = "会话 ID") @RequestParam id: String,
        request: HttpServletRequest,
    ): MessageDto {
        val userId = AuthGuards.requireUserId(request)
        sessionService.deleteSession(userId, id)
        return MessageDto("会话已删除")
    }
}
