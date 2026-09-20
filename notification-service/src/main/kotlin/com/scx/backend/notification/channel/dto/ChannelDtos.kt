package com.scx.backend.notification.channel.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.scx.backend.notification.entity.Channel
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 创建渠道请求 */
@Schema(description = "创建渠道请求")
data class CreateChannelDto(
    @Schema(description = "渠道编码（大写字母开头，大写字母/数字/下划线，2-50 字符，唯一）", required = true)
    @field:NotBlank(message = "渠道编码不能为空")
    @field:Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "渠道编码须为大写字母开头的大写字母/数字/下划线组合")
    val code: String,

    @Schema(description = "渠道名称（2-50 字符）", required = true)
    @field:NotBlank(message = "渠道名称不能为空")
    @field:Size(min = 2, max = 50, message = "渠道名称长度必须在2-50个字符之间")
    val name: String,

    @Schema(description = "投递类型：EMAIL（邮件）/ INBOX（站内信）/ ANNOUNCEMENT（公告）", required = true)
    @field:NotBlank(message = "投递类型不能为空")
    @field:Pattern(regexp = "EMAIL|INBOX|ANNOUNCEMENT", message = "投递类型必须为 EMAIL/INBOX/ANNOUNCEMENT")
    val type: String,

    @Schema(description = "渠道描述（最长 255 字符）")
    @field:Size(max = 255, message = "渠道描述不能超过255个字符")
    val description: String? = null,
)

/** 更新渠道请求（code 与 type 创建后不可改，系统渠道同规则） */
@Schema(description = "更新渠道请求")
data class UpdateChannelDto(
    @Schema(description = "渠道 ID", required = true)
    @field:NotBlank(message = "渠道ID不能为空")
    val id: String,

    @Schema(description = "渠道名称（2-50 字符）")
    @field:Size(min = 2, max = 50, message = "渠道名称长度必须在2-50个字符之间")
    val name: String? = null,

    @Schema(description = "渠道描述（最长 255 字符）")
    @field:Size(max = 255, message = "渠道描述不能超过255个字符")
    val description: String? = null,

    @Schema(description = "是否启用（停用后不可用于发消息）")
    val isActive: Boolean? = null,
)

/** 渠道列表响应 */
@Schema(description = "渠道列表响应")
data class ChannelListResponseDto(
    @Schema(description = "渠道列表") val list: List<ChannelResponseDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "当前页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)

/** 渠道信息响应 */
@Schema(description = "渠道信息响应")
data class ChannelResponseDto(
    @Schema(description = "渠道 ID") val id: String,
    @Schema(description = "渠道编码") val code: String,
    @Schema(description = "渠道名称") val name: String,
    @Schema(description = "投递类型 EMAIL/INBOX/ANNOUNCEMENT") val type: String,
    @Schema(description = "渠道描述") val description: String?,
    @Schema(description = "是否系统渠道（不可删除）") @get:JsonProperty("isSystem") val isSystem: Boolean,
    @Schema(description = "是否启用") @get:JsonProperty("isActive") val isActive: Boolean,
    @Schema(description = "创建时间") val createdAt: java.time.LocalDateTime,
    @Schema(description = "更新时间") val updatedAt: java.time.LocalDateTime,
) {
    companion object {
        /** @description 实体转响应 DTO */
        fun from(channel: Channel): ChannelResponseDto = ChannelResponseDto(
            id = channel.id,
            code = channel.code,
            name = channel.name,
            type = channel.type,
            description = channel.description,
            isSystem = channel.isSystem,
            isActive = channel.isActive,
            createdAt = channel.createdAt,
            updatedAt = channel.updatedAt,
        )
    }
}
