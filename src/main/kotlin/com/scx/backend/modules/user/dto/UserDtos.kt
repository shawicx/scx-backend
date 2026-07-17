package com.scx.backend.modules.user.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// ============ 请求 DTO ============

/** 用户注册（对标 RegisterUserDto） */
data class RegisterUserDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @field:NotBlank(message = "用户名称不能为空")
    @field:Size(min = 2, max = 50, message = "用户名称长度必须在2-50个字符之间")
    val name: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 50, message = "密码长度必须在8-50个字符之间")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]+$",
        message = "密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符",
    )
    val password: String,

    @field:NotBlank(message = "验证码不能为空")
    @field:Pattern(regexp = "^\\d{6}$", message = "验证码必须是6位数字")
    val emailVerificationCode: String,
)

/** 验证码登录（对标 LoginUserDto） */
data class LoginUserDto(
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,
    @field:Size(min = 6, message = "验证码长度为6位")
    val emailVerificationCode: String,
)

/** 密码登录（对标 LoginWithPasswordDto） */
data class LoginWithPasswordDto(
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,
    @field:Size(min = 6, message = "密码至少6位")
    val password: String,
    @field:NotBlank(message = "密钥ID不能为空")
    val keyId: String,
)

/** 管理员创建用户（对标 CreateUserDto） */
data class CreateUserDto(
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @field:NotBlank(message = "用户名称不能为空")
    @field:Size(min = 2, max = 50, message = "用户名称长度必须在2-50个字符之间")
    val name: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 50, message = "密码长度必须在8-50个字符之间")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]+$",
        message = "密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符",
    )
    val password: String,

    val isActive: Boolean = true,

    @field:Size(max = 10, message = "最多分配10个角色")
    val roleIds: List<String>? = null,
)

/** 批量删除用户（对标 DeleteUsersDto） */
data class DeleteUsersDto(
    @field:NotEmpty(message = "用户ID列表不能为空")
    @field:Size(max = 50, message = "最多一次删除50个用户")
    @field:Valid
    val userIds: List<@Pattern(
        regexp = "^[0-7][0-9A-HJKMNP-TV-Z]{25}$",
        flags = [Pattern.Flag.CASE_INSENSITIVE],
        message = "用户ID必须是有效的ULID",
    ) String>,
)

/** 切换用户状态（对标 ToggleUserStatusDto） */
data class ToggleUserStatusDto(
    @field:NotEmpty(message = "用户ID列表不能为空")
    @field:Size(max = 50, message = "最多一次操作50个用户")
    @field:Valid
    val userIds: List<@Pattern(
        regexp = "^[0-7][0-9A-HJKMNP-TV-Z]{25}$",
        flags = [Pattern.Flag.CASE_INSENSITIVE],
        message = "用户ID必须是有效的ULID",
    ) String>,
    val isActive: Boolean,
)

/** 分配单个角色（对标 AssignRoleDto） */
data class AssignRoleDto(
    @field:NotBlank(message = "角色ID不能为空") val roleId: String,
    @field:NotBlank(message = "用户ID不能为空") val userId: String,
)

/** 批量分配角色（对标 AssignRolesDto） */
data class AssignRolesDto(
    @field:NotEmpty(message = "角色ID列表不能为空")
    val roleIds: List<String>,
    @field:NotBlank(message = "用户ID不能为空") val userId: String,
)

/** 用户列表查询（对标 QueryUsersDto） */
data class QueryUsersDto(
    val page: Int = 1,
    val limit: Int = 10,
    val search: String? = null,
    val isActive: Boolean? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC",
)

/** 通用消息体（对标 { message: string }） */
data class MessageDto(val message: String)

/** 删除/状态切换结果（对标 { count, message }） */
data class CountResultDto(val count: Int, val message: String)

/** 令牌刷新请求 */
data class RefreshTokenDto(val refreshToken: String)

/** 发送验证码请求 */
data class SendCodeDto(
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,
)
