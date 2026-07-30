/**
 * @description 用户偏好设置实体（对应 users.preferences jsonb 列的强类型结构）。
 * 定义于 entity 包，供 User 实体及各响应 DTO 共用，避免实体层反向依赖 dto 层。
 */
package com.scx.backend.entity

import io.swagger.v3.oas.annotations.media.Schema

/**
 * @description 用户偏好设置
 */
@Schema(description = "用户偏好设置")
data class UserPreferences(
    @Schema(description = "主题", defaultValue = "light")
    val theme: String? = "light",

    @Schema(description = "语言", defaultValue = "zh-CN")
    val language: String? = "zh-CN",

    @Schema(description = "时区", defaultValue = "Asia/Shanghai")
    val timezone: String? = "Asia/Shanghai",

    @Schema(description = "通知偏好")
    val notifications: NotificationPrefs? = null,

    @Schema(description = "隐私偏好")
    val privacy: PrivacyPrefs? = null,
) {
    /**
     * @description 通知偏好
     */
    @Schema(description = "通知偏好")
    data class NotificationPrefs(
        @Schema(description = "邮件通知")
        val email: Boolean? = true,
        @Schema(description = "推送通知")
        val push: Boolean? = true,
        @Schema(description = "短信通知")
        val sms: Boolean? = false,
    )

    /**
     * @description 隐私偏好
     */
    @Schema(description = "隐私偏好")
    data class PrivacyPrefs(
        @Schema(description = "资料是否可见")
        val profileVisible: Boolean? = true,
        @Schema(description = "是否展示邮箱")
        val showEmail: Boolean? = false,
        @Schema(description = "是否展示最后在线时间")
        val showLastSeen: Boolean? = true,
    )
}
