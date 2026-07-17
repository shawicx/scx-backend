package com.scx.backend.modules.user.dto

import com.fasterxml.jackson.databind.JsonNode
import com.scx.backend.entity.User

/**
 * 用户偏好设置（对标 UserPreferences，排除 AI 子结构）
 */
data class UserPreferences(
    val theme: String? = "light",
    val language: String? = "zh-CN",
    val timezone: String? = "Asia/Shanghai",
    val notifications: NotificationPrefs? = null,
    val privacy: PrivacyPrefs? = null,
) {
    data class NotificationPrefs(
        val email: Boolean? = true,
        val push: Boolean? = true,
        val sms: Boolean? = false,
    )

    data class PrivacyPrefs(
        val profileVisible: Boolean? = true,
        val showEmail: Boolean? = false,
        val showLastSeen: Boolean? = true,
    )
}

/**
 * 用户响应（对标 UserResponseDto，使用 @Exclude 隐藏 password 等敏感字段）
 * 对标源 ClassSerializerInterceptor 的 @Exclude：password 不输出
 */
data class UserResponseDto(
    val id: String,
    val email: String,
    val name: String,
    val emailVerified: Boolean,
    val preferences: JsonNode?,
    val lastLoginIp: String?,
    val lastLoginAt: java.time.LocalDateTime?,
    val loginCount: Int,
    val isActive: Boolean,
    val createdAt: java.time.LocalDateTime,
    val updatedAt: java.time.LocalDateTime,
) {
    companion object {
        fun from(user: User): UserResponseDto = UserResponseDto(
            id = user.id,
            email = user.email,
            name = user.name,
            emailVerified = user.emailVerified,
            preferences = user.preferences,
            lastLoginIp = user.lastLoginIp,
            lastLoginAt = user.lastLoginAt,
            loginCount = user.loginCount,
            isActive = user.isActive,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
        )
    }
}

/**
 * 登录响应（对标 LoginResponseDto）
 */
data class LoginResponseDto(
    val id: String,
    val email: String,
    val name: String,
    val emailVerified: Boolean,
    val preferences: JsonNode?,
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(user: User, accessToken: String, refreshToken: String): LoginResponseDto = LoginResponseDto(
            id = user.id,
            email = user.email,
            name = user.name,
            emailVerified = user.emailVerified,
            preferences = user.preferences,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }
}

/**
 * 用户角色关系响应（对标 UserRoleResponseDto）
 */
data class UserRoleResponseDto(
    val id: String,
    val userId: String,
    val roleId: String,
    val createdAt: java.time.LocalDateTime,
)

/**
 * 用户列表项（对标 UserListItemDto）
 */
data class UserListItemDto(
    val id: String,
    val email: String,
    val name: String,
    val emailVerified: Boolean,
    val isActive: Boolean,
    val lastLoginAt: java.time.LocalDateTime?,
    val loginCount: Int,
    val createdAt: java.time.LocalDateTime,
)

/**
 * 用户列表响应（对标 UserListResponseDto）
 */
data class UserListResponseDto(
    val list: List<UserListItemDto>,
    val total: Long,
    val page: Int,
    val limit: Int,
)
