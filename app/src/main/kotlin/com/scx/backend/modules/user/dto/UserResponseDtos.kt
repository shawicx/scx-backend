package com.scx.backend.modules.user.dto

import com.scx.backend.entity.User
import com.scx.backend.entity.UserPreferences
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 用户信息响应
 *
 * 不输出 password 等敏感字段
 */
@Schema(description = "用户信息响应")
data class UserResponseDto(
    @Schema(description = "用户 ID")
    val id: String,

    @Schema(description = "邮箱")
    val email: String,

    @Schema(description = "用户名称")
    val name: String,

    @Schema(description = "邮箱是否已验证")
    val emailVerified: Boolean,

    @Schema(description = "偏好设置")
    val preferences: UserPreferences?,

    @Schema(description = "最后登录 IP")
    val lastLoginIp: String?,

    @Schema(description = "最后登录时间")
    val lastLoginAt: java.time.LocalDateTime?,

    @Schema(description = "登录次数")
    val loginCount: Int,

    @Schema(description = "是否启用")
    val isActive: Boolean,

    @Schema(description = "创建时间")
    val createdAt: java.time.LocalDateTime,

    @Schema(description = "更新时间")
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
 * 登录响应
 */
@Schema(description = "登录响应")
data class LoginResponseDto(
    @Schema(description = "用户 ID")
    val id: String,

    @Schema(description = "邮箱")
    val email: String,

    @Schema(description = "用户名称")
    val name: String,

    @Schema(description = "邮箱是否已验证")
    val emailVerified: Boolean,

    @Schema(description = "偏好设置")
    val preferences: UserPreferences?,

    @Schema(description = "访问令牌（2 小时有效）")
    val accessToken: String,

    @Schema(description = "刷新令牌（7 天有效）")
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
 * 用户-角色关系响应
 */
@Schema(description = "用户-角色关系响应")
data class UserRoleResponseDto(
    @Schema(description = "关联记录 ID")
    val id: String,

    @Schema(description = "用户 ID")
    val userId: String,

    @Schema(description = "角色 ID")
    val roleId: String,

    @Schema(description = "创建时间")
    val createdAt: java.time.LocalDateTime,
)

/**
 * 用户列表项
 */
@Schema(description = "用户列表项")
data class UserListItemDto(
    @Schema(description = "用户 ID")
    val id: String,

    @Schema(description = "邮箱")
    val email: String,

    @Schema(description = "用户名称")
    val name: String,

    @Schema(description = "邮箱是否已验证")
    val emailVerified: Boolean,

    @Schema(description = "是否启用")
    val isActive: Boolean,

    @Schema(description = "最后登录时间")
    val lastLoginAt: java.time.LocalDateTime?,

    @Schema(description = "登录次数")
    val loginCount: Int,

    @Schema(description = "创建时间")
    val createdAt: java.time.LocalDateTime,
)

/**
 * 用户列表响应
 */
@Schema(description = "用户列表响应")
data class UserListResponseDto(
    @Schema(description = "用户列表")
    val list: List<UserListItemDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)
