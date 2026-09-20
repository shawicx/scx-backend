package com.scx.backend.notification.message

import com.scx.backend.common.dto.MessageDto
import com.scx.backend.notification.message.dto.InboxListResponseDto
import com.scx.backend.notification.message.dto.MarkAllReadDto
import com.scx.backend.notification.message.dto.MarkReadDto
import com.scx.backend.notification.message.dto.UnreadCountDto
import com.scx.backend.notification.security.AuthGuards
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description 用户端收件箱控制器（需登录，取 X-User-Id）
 *
 * 与管理端同前缀 /notifications，仅子路径不同（Spring 允许多控制器共享 RequestMapping 前缀）。
 */
@Tag(name = "用户消息", description = "收件箱、公告列表、未读数与已读标记")
@RestController
@RequestMapping("/notifications", produces = [MediaType.APPLICATION_JSON_VALUE])
class InboxController(
    private val inboxService: InboxService,
) {
    @Operation(summary = "我的收件箱", description = "INBOX 渠道消息，未读在前、时间倒序")
    @GetMapping("/inbox")
    fun inbox(
        request: HttpServletRequest,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
    ): InboxListResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return inboxService.inbox(userId, page, limit)
    }

    @Operation(summary = "未读数", description = "收件箱与公告分桶未读计数（角标）")
    @GetMapping("/unread-count")
    fun unreadCount(request: HttpServletRequest): UnreadCountDto =
        inboxService.unreadCount(AuthGuards.requireUserId(request))

    @Operation(summary = "公告列表", description = "ANNOUNCEMENT 渠道消息，置顶在前、时间倒序，含已读状态")
    @GetMapping("/announcements")
    fun announcements(
        request: HttpServletRequest,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
    ): InboxListResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return inboxService.announcements(userId, page, limit)
    }

    @Operation(summary = "标记单条已读", description = "只能标记本人消息；非本人按不存在处理")
    @PutMapping("/read")
    fun read(@Valid @RequestBody dto: MarkReadDto, request: HttpServletRequest): MessageDto {
        val userId = AuthGuards.requireUserId(request)
        inboxService.markRead(userId, dto.notificationId)
        return MessageDto("已标记为已读")
    }

    @Operation(summary = "全部已读", description = "可携带 channelIds 缩小标记范围")
    @PutMapping("/read-all")
    fun readAll(@Valid @RequestBody dto: MarkAllReadDto, request: HttpServletRequest): MessageDto {
        val userId = AuthGuards.requireUserId(request)
        val count = inboxService.markAllRead(userId, dto.channelIds)
        return MessageDto("已标记 $count 条为已读")
    }
}
