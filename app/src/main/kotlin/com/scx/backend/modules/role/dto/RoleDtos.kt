package com.scx.backend.modules.role.dto

import com.scx.backend.entity.Role
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 创建角色请求 */
@Schema(description = "创建角色请求")
data class CreateRoleDto(
    @Schema(description = "角色名称（2-50 字符）", required = true)
    @field:NotBlank(message = "角色名称不能为空")
    @field:Size(min = 2, max = 50, message = "角色名称长度必须在2-50个字符之间")
    val name: String,

    @Schema(description = "角色编码（2-50 字符，唯一）", required = true)
    @field:NotBlank(message = "角色代码不能为空")
    @field:Size(min = 2, max = 50, message = "角色代码长度必须在2-50个字符之间")
    val code: String,

    @Schema(description = "角色描述（最长 255 字符）")
    @field:Size(max = 255, message = "角色描述不能超过255个字符")
    val description: String? = null,

    @Schema(description = "是否系统内置角色（系统角色不可删除）")
    val isSystem: Boolean? = false,
)

/** 更新角色请求（ID 在 body 中） */
@Schema(description = "更新角色请求")
data class UpdateRoleDto(
    @Schema(description = "角色 ID", required = true)
    @field:NotBlank(message = "角色ID不能为空")
    val id: String,

    @Schema(description = "角色名称（2-50 字符）")
    @field:Size(min = 2, max = 50, message = "角色名称长度必须在2-50个字符之间")
    val name: String? = null,

    @Schema(description = "角色编码（2-50 字符）")
    @field:Size(min = 2, max = 50, message = "角色代码长度必须在2-50个字符之间")
    val code: String? = null,

    @Schema(description = "角色描述（最长 255 字符）")
    @field:Size(max = 255, message = "角色描述不能超过255个字符")
    val description: String? = null,

    @Schema(description = "是否系统内置角色")
    val isSystem: Boolean? = null,
)

/** 为角色分配权限请求（ID 在 body 中） */
@Schema(description = "为角色分配权限请求")
data class AssignPermissionsDto(
    @Schema(description = "角色 ID", required = true)
    @field:NotBlank(message = "角色ID不能为空")
    val id: String,

    @Schema(description = "权限 ID 列表", required = true)
    val permissionIds: List<String>,
)

/** 角色信息响应 */
@Schema(description = "角色信息响应")
data class RoleResponseDto(
    @Schema(description = "角色 ID")
    val id: String,

    @Schema(description = "角色名称")
    val name: String,

    @Schema(description = "角色编码")
    val code: String,

    @Schema(description = "角色描述")
    val description: String?,

    @Schema(description = "是否系统内置角色")
    val isSystem: Boolean,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "更新时间")
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(role: Role): RoleResponseDto = RoleResponseDto(
            id = role.id,
            name = role.name,
            code = role.code,
            description = role.description,
            isSystem = role.isSystem,
            createdAt = role.createdAt,
            updatedAt = role.updatedAt,
        )
    }
}
