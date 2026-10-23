package com.scx.backend.blog.interaction.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * @description 博客互动相关 DTO（阅读上报 / 点赞状态）
 */

/** 点赞状态响应 */
@Schema(description = "点赞状态响应")
data class LikeStatusDto(
    @Schema(description = "当前有效点赞数") val count: Long,
    @Schema(description = "当前访客是否已点赞") val liked: Boolean,
)
