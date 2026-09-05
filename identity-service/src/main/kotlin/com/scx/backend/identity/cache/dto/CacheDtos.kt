package com.scx.backend.identity.cache.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * @description Redis 连接信息响应
 *
 * @property isOpen 连接是否可用（ping 探活）
 * @property isReady 是否就绪（与 isOpen 一致）
 * @property status 状态描述（ready / closed / unknown）
 *
 * @example RedisConnectionInfoDto(isOpen = true, isReady = true, status = "ready")
 */
@Schema(description = "Redis 连接信息响应")
data class RedisConnectionInfoDto(
    @Schema(description = "连接是否可用（ping 探活）")
    @get:JsonProperty("isOpen")
    val isOpen: Boolean,

    @Schema(description = "是否就绪（与 isOpen 一致）")
    @get:JsonProperty("isReady")
    val isReady: Boolean,

    @Schema(description = "状态描述（ready / closed / unknown）")
    val status: String,
)
