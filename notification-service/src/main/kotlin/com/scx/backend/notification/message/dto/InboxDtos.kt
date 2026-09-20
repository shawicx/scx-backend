package com.scx.backend.notification.message.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 收件箱条目 */
@Schema(description = "收件箱/公告条目")
data class InboxItemDto(
    @Schema(description = "投递记录 ID") val id: String,
    @Schema(description = "消息 ID") val notificationId: String,
    @Schema(description = "标题") val title: String,
    @Schema(description = "内容") val content: String,
    @Schema(description = "前端跳转路由") val link: String?,
    @Schema(description = "级别 INFO/WARNING/URGENT") val level: String,
    @Schema(description = "渠道编码") val channelCode: String,
    @Schema(description = "渠道名称") val channelName: String,
    @Schema(description = "是否置顶（公告）") val pinned: Boolean,
    @Schema(description = "是否已读") val read: Boolean,
    @Schema(description = "已读时间") val readAt: LocalDateTime?,
    @Schema(description = "发送时间") val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 投递行实体转收件箱条目 DTO
         * @param un 用户投递行（notification 已 JOIN FETCH）
         */
        fun from(un: com.scx.backend.notification.entity.UserNotification): InboxItemDto = InboxItemDto(
            id = un.id,
            notificationId = un.notification.id,
            title = un.notification.title,
            content = un.notification.content,
            link = un.notification.link,
            level = un.notification.level,
            channelCode = un.notification.channel.code,
            channelName = un.notification.channel.name,
            pinned = un.notification.pinned,
            read = un.readAt != null,
            readAt = un.readAt,
            createdAt = un.notification.createdAt,
        )
    }
}

/** 收件箱分页响应 */
@Schema(description = "收件箱分页响应")
data class InboxListResponseDto(
    @Schema(description = "条目列表") val list: List<InboxItemDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "当前页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)

/** 未读数响应（分桶） */
@Schema(description = "未读数响应")
data class UnreadCountDto(
    @Schema(description = "站内信未读数") val inbox: Long,
    @Schema(description = "公告未读数") val announcement: Long,
)

/** 标记单条已读请求 */
@Schema(description = "标记单条已读请求")
data class MarkReadDto(
    @Schema(description = "消息 ID", required = true)
    @field:jakarta.validation.constraints.NotBlank(message = "消息ID不能为空")
    val notificationId: String,
)

/** 全部已读请求 */
@Schema(description = "全部已读请求")
data class MarkAllReadDto(
    @Schema(description = "渠道 ID 列表（可选，缩小标记范围）")
    val channelIds: List<String>? = null,
)
