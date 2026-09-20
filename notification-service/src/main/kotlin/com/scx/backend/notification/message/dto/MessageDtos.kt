package com.scx.backend.notification.message.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 发送消息请求 */
@Schema(description = "发送消息请求")
data class SendMessageDto(
    @Schema(description = "渠道 ID（必填——发消息必须指定渠道）", required = true)
    @field:NotBlank(message = "渠道ID不能为空")
    val channelId: String,

    @Schema(description = "消息标题（最长 200 字符）", required = true)
    @field:NotBlank(message = "标题不能为空")
    @field:Size(max = 200, message = "标题不能超过200个字符")
    val title: String,

    @Schema(description = "消息内容（站内信纯文本/HTML，EMAIL 渠道按 HTML 投递）", required = true)
    @field:NotBlank(message = "内容不能为空")
    val content: String,

    @Schema(description = "目标类型：ALL（全体）/ USERS（指定用户）/ ROLES（指定角色）", required = true)
    @field:NotBlank(message = "目标类型不能为空")
    @field:Pattern(regexp = "ALL|USERS|ROLES", message = "目标类型必须为 ALL/USERS/ROLES")
    val targetType: String,

    @Schema(description = "目标 ID 列表（USERS 为用户 ID、ROLES 为角色 ID；ALL 时忽略）")
    @field:Size(max = 1000, message = "单次目标数不能超过1000")
    val targetIds: List<String>? = null,

    @Schema(description = "前端跳转路由（可选，最长 200 字符）")
    @field:Size(max = 200, message = "跳转链接不能超过200个字符")
    val link: String? = null,

    @Schema(description = "级别：INFO/WARNING/URGENT（公告字段，默认 INFO）")
    @field:Pattern(regexp = "INFO|WARNING|URGENT", message = "级别必须为 INFO/WARNING/URGENT")
    val level: String? = null,

    @Schema(description = "是否置顶（公告字段，默认 false）")
    val pinned: Boolean? = false,
)

/** 发送结果 */
@Schema(description = "发送消息结果")
data class SendResultDto(
    @Schema(description = "消息 ID") val notificationId: String,
    @Schema(description = "实际投递人数") val recipientCount: Int,
)
