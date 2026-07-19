package com.scx.backend.modules.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// ============ 请求 DTO ============

/** 用户注册请求 */
@Schema(description = "用户注册请求")
data class RegisterUserDto(
    @Schema(description = "邮箱地址", example = "user@example.com", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @Schema(description = "用户名称（2-50 字符）", example = "张三", required = true)
    @field:NotBlank(message = "用户名称不能为空")
    @field:Size(min = 2, max = 50, message = "用户名称长度必须在2-50个字符之间")
    val name: String,

    @Schema(description = "密码（8-50 字符，需含大小写字母、数字和特殊字符）", required = true)
    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 50, message = "密码长度必须在8-50个字符之间")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]+$",
        message = "密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符",
    )
    val password: String,

    @Schema(description = "邮箱验证码（6 位数字）", example = "123456", required = true)
    @field:NotBlank(message = "验证码不能为空")
    @field:Pattern(regexp = "^\\d{6}$", message = "验证码必须是6位数字")
    val emailVerificationCode: String,
)

/** 邮箱验证码登录请求 */
@Schema(description = "邮箱验证码登录请求")
data class LoginUserDto(
    @Schema(description = "邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,

    @Schema(description = "邮箱验证码（6 位）", required = true)
    @field:Size(min = 6, message = "验证码长度为6位")
    val emailVerificationCode: String,
)

/** 密码登录请求 */
@Schema(description = "密码登录请求")
data class LoginWithPasswordDto(
    @Schema(description = "邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,

    @Schema(description = "前端加密后的密码（AES）", required = true)
    @field:Size(min = 6, message = "密码至少6位")
    val password: String,

    @Schema(description = "加密密钥 ID（来自 /users/encryption-key）", required = true)
    @field:NotBlank(message = "密钥ID不能为空")
    val keyId: String,
)

/** 管理员创建用户请求 */
@Schema(description = "管理员创建用户请求")
data class CreateUserDto(
    @Schema(description = "邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @Schema(description = "用户名称（2-50 字符）", required = true)
    @field:NotBlank(message = "用户名称不能为空")
    @field:Size(min = 2, max = 50, message = "用户名称长度必须在2-50个字符之间")
    val name: String,

    @Schema(description = "密码（8-50 字符，需含大小写字母、数字和特殊字符）", required = true)
    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 50, message = "密码长度必须在8-50个字符之间")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]+$",
        message = "密码必须包含至少一个大写字母、一个小写字母、一个数字和一个特殊字符",
    )
    val password: String,

    @Schema(description = "是否启用", defaultValue = "true")
    val isActive: Boolean = true,

    @Schema(description = "初始分配的角色 ID 列表（最多 10 个）")
    @field:Size(max = 10, message = "最多分配10个角色")
    val roleIds: List<String>? = null,
)

/** 批量删除用户请求 */
@Schema(description = "批量删除用户请求")
data class DeleteUsersDto(
    @Schema(description = "用户 ID 列表（ULID，最多 50 个）", required = true)
    @field:NotEmpty(message = "用户ID列表不能为空")
    @field:Size(max = 50, message = "最多一次删除50个用户")
    @field:Valid
    val userIds: List<@Pattern(
        regexp = "^[0-7][0-9A-HJKMNP-TV-Z]{25}$",
        flags = [Pattern.Flag.CASE_INSENSITIVE],
        message = "用户ID必须是有效的ULID",
    ) String>,
)

/** 批量切换用户状态请求 */
@Schema(description = "批量切换用户状态请求")
data class ToggleUserStatusDto(
    @Schema(description = "用户 ID 列表（ULID，最多 50 个）", required = true)
    @field:NotEmpty(message = "用户ID列表不能为空")
    @field:Size(max = 50, message = "最多一次操作50个用户")
    @field:Valid
    val userIds: List<@Pattern(
        regexp = "^[0-7][0-9A-HJKMNP-TV-Z]{25}$",
        flags = [Pattern.Flag.CASE_INSENSITIVE],
        message = "用户ID必须是有效的ULID",
    ) String>,

    @Schema(description = "目标状态：true 启用，false 停用", required = true)
    val isActive: Boolean,
)

/** 分配单个角色请求 */
@Schema(description = "分配单个角色请求")
data class AssignRoleDto(
    @Schema(description = "角色 ID", required = true)
    @field:NotBlank(message = "角色ID不能为空") val roleId: String,

    @Schema(description = "用户 ID", required = true)
    @field:NotBlank(message = "用户ID不能为空") val userId: String,
)

/** 批量分配角色请求 */
@Schema(description = "批量分配角色请求")
data class AssignRolesDto(
    @Schema(description = "角色 ID 列表", required = true)
    @field:NotEmpty(message = "角色ID列表不能为空")
    val roleIds: List<String>,

    @Schema(description = "用户 ID", required = true)
    @field:NotBlank(message = "用户ID不能为空") val userId: String,
)

/** 用户列表查询参数 */
@Schema(description = "用户列表查询参数")
data class QueryUsersDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字（邮箱或用户名）")
    val search: String? = null,

    @Schema(description = "按状态过滤：true 启用，false 停用")
    val isActive: Boolean? = null,

    @Schema(description = "排序字段", defaultValue = "createdAt")
    val sortBy: String = "createdAt",

    @Schema(description = "排序方向：ASC / DESC", defaultValue = "DESC")
    val sortOrder: String = "DESC",
)

/** 通用消息响应 */
@Schema(description = "通用消息响应")
data class MessageDto(
    @Schema(description = "提示消息")
    val message: String,
)

/** 批量操作结果响应 */
@Schema(description = "批量操作结果响应")
data class CountResultDto(
    @Schema(description = "受影响行数")
    val count: Int,

    @Schema(description = "提示消息")
    val message: String,
)

/** 刷新令牌请求 */
@Schema(description = "刷新令牌请求")
data class RefreshTokenDto(
    @Schema(description = "刷新令牌", required = true)
    val refreshToken: String,
)

/** 发送验证码请求 */
@Schema(description = "发送验证码请求")
data class SendCodeDto(
    @Schema(description = "邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    val email: String,
)
