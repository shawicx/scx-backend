package com.scx.backend.notification.message.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 管理端消息条目 */
@Schema(description = "管理端消息条目")
data class NotificationAdminDto(
    @Schema(description = "消息 ID") val id: String,
    @Schema(description = "渠道 ID") val channelId: String,
    @Schema(description = "渠道编码") val channelCode: String,
    @Schema(description = "渠道名称") val channelName: String,
    @Schema(description = "标题") val title: String,
    @Schema(description = "级别") val level: String,
    @Schema(description = "是否置顶") val pinned: Boolean,
    @Schema(description = "目标类型 ALL/USERS/ROLES/DIRECT") val targetType: String,
    @Schema(description = "目标 ID JSON 字符串（审计）") val targetIds: String?,
    @Schema(description = "事务邮件收件邮箱") val recipientEmail: String?,
    @Schema(description = "实际投递人数") val recipientCount: Int,
    @Schema(description = "发送者用户 ID") val senderId: String?,
    @Schema(description = "发送时间") val createdAt: LocalDateTime,
) {
    companion object {
        /** @description 实体转管理端 DTO */
        fun from(n: com.scx.backend.notification.entity.Notification) = NotificationAdminDto(
            id = n.id,
            channelId = n.channel.id,
            channelCode = n.channel.code,
            channelName = n.channel.name,
            title = n.title,
            level = n.level,
            pinned = n.pinned,
            targetType = n.targetType,
            targetIds = n.targetIds,
            recipientEmail = n.recipientEmail,
            recipientCount = n.recipientCount,
            senderId = n.senderId,
            createdAt = n.createdAt,
        )
    }
}

/** 管理端消息详情（targetIds 解析为列表） */
@Schema(description = "管理端消息详情")
data class NotificationDetailDto(
    @Schema(description = "基础信息") val notification: NotificationAdminDto,
    @Schema(description = "目标 ID 列表") val targetIdList: List<String>?,
)

/** 管理端消息分页响应 */
@Schema(description = "管理端消息分页响应")
data class MessageListResponseDto(
    @Schema(description = "消息列表") val list: List<NotificationAdminDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "当前页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)
