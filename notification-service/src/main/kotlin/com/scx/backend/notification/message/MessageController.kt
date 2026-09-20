package com.scx.backend.notification.message

import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.notification.message.dto.MessageListResponseDto
import com.scx.backend.notification.message.dto.NotificationDetailDto
import com.scx.backend.notification.message.dto.SendMessageDto
import com.scx.backend.notification.message.dto.SendResultDto
import com.scx.backend.notification.security.AuthGuards
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description 消息管理控制器（管理端：发送 / 列表 / 详情，需管理员）
 *
 * 路由前缀 /api/notifications（context-path=/api）
 */
@Tag(name = "消息管理", description = "站内信/公告的发送（全局/用户/角色）与发送记录查询")
@RestController
@RequestMapping("/notifications", produces = [MediaType.APPLICATION_JSON_VALUE])
class MessageController(
    private val messageService: MessageService,
) {
    @OperationLog(module = "消息管理", action = "发送消息")
    @Operation(summary = "发送消息", description = "必须指定渠道；targetType=ALL 全体广播、USERS/ROLES 定向")
    @PostMapping("/send")
    fun send(@Valid @RequestBody dto: SendMessageDto, request: HttpServletRequest): SendResultDto {
        val principal = AuthGuards.requireAdmin(request)
        return messageService.send(dto, principal.userId)
    }

    @Operation(summary = "消息分页列表", description = "管理端发送记录，按渠道/目标类型筛选")
    @GetMapping("/list")
    fun list(
        request: HttpServletRequest,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
        @Parameter(description = "渠道 ID") @RequestParam(required = false) channelId: String?,
        @Parameter(description = "目标类型 ALL/USERS/ROLES/DIRECT") @RequestParam(required = false) targetType: String?,
    ): MessageListResponseDto {
        AuthGuards.requireAdmin(request)
        return messageService.findAll(page, limit, channelId, targetType)
    }

    @Operation(summary = "消息详情", description = "含目标 ID 审计信息")
    @GetMapping("/detail")
    fun detail(
        @Parameter(description = "消息 ID") @RequestParam id: String,
        request: HttpServletRequest,
    ): NotificationDetailDto {
        AuthGuards.requireAdmin(request)
        return messageService.findById(id)
    }
}
