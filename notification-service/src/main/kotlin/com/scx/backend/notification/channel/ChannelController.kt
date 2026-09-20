package com.scx.backend.notification.channel

import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.notification.channel.dto.ChannelListResponseDto
import com.scx.backend.notification.channel.dto.ChannelResponseDto
import com.scx.backend.notification.channel.dto.CreateChannelDto
import com.scx.backend.notification.channel.dto.UpdateChannelDto
import com.scx.backend.notification.security.AuthGuards
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description 渠道管理控制器（管理端，需管理员）
 *
 * 路由前缀 /api/channels（context-path=/api）；身份经网关注入的 X-User-* 头解析。
 */
@Tag(name = "渠道管理", description = "消息渠道的创建、查询、更新与删除（系统渠道不可删除）")
@RestController
@RequestMapping("/channels", produces = [MediaType.APPLICATION_JSON_VALUE])
class ChannelController(
    private val channelService: ChannelService,
) {
    @OperationLog(module = "渠道管理", action = "创建渠道")
    @Operation(summary = "创建渠道", description = "新建自定义渠道（code 唯一，type 创建后不可改）")
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreateChannelDto, request: HttpServletRequest): ChannelResponseDto {
        AuthGuards.requireAdmin(request)
        return channelService.create(dto)
    }

    @Operation(summary = "渠道分页列表", description = "按投递类型与关键字（code/name 模糊）筛选")
    @GetMapping("/list")
    fun list(
        request: HttpServletRequest,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
        @Parameter(description = "投递类型 EMAIL/INBOX/ANNOUNCEMENT") @RequestParam(required = false) type: String?,
        @Parameter(description = "关键字（code/name 模糊）") @RequestParam(required = false) keyword: String?,
    ): ChannelListResponseDto {
        AuthGuards.requireAdmin(request)
        return channelService.findAll(page, limit, type, keyword)
    }

    @Operation(summary = "全量启用渠道", description = "不分页返回启用中的渠道，供发消息下拉一次加载")
    @GetMapping("/all")
    fun all(request: HttpServletRequest): List<ChannelResponseDto> {
        AuthGuards.requireAdmin(request)
        return channelService.getAllActive()
    }

    @OperationLog(module = "渠道管理", action = "更新渠道")
    @Operation(summary = "更新渠道", description = "仅允许更新名称/描述/启用状态")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateChannelDto, request: HttpServletRequest): ChannelResponseDto {
        AuthGuards.requireAdmin(request)
        return channelService.update(dto)
    }

    @OperationLog(module = "渠道管理", action = "删除渠道")
    @Operation(summary = "删除渠道", description = "系统渠道（isSystem=true）不可删除")
    @DeleteMapping("/delete")
    fun delete(
        @Parameter(description = "渠道 ID") @RequestParam id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        channelService.delete(id)
        return MessageDto("渠道删除成功")
    }
}
