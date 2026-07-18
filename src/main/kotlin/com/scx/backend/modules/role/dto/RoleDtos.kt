package com.scx.backend.modules.role.dto

import com.scx.backend.entity.Role
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 创建角色（对标 CreateRoleDto） */
data class CreateRoleDto(
    @field:NotBlank(message = "角色名称不能为空")
    @field:Size(min = 2, max = 50, message = "角色名称长度必须在2-50个字符之间")
    val name: String,
    @field:NotBlank(message = "角色代码不能为空")
    @field:Size(min = 2, max = 50, message = "角色代码长度必须在2-50个字符之间")
    val code: String,
    @field:Size(max = 255, message = "角色描述不能超过255个字符")
    val description: String? = null,
    val isSystem: Boolean? = false,
)

/** 更新角色（对标 UpdateRoleDto，id 在 body 中） */
data class UpdateRoleDto(
    @field:NotBlank(message = "角色ID不能为空")
    val id: String,
    @field:Size(min = 2, max = 50, message = "角色名称长度必须在2-50个字符之间")
    val name: String? = null,
    @field:Size(min = 2, max = 50, message = "角色代码长度必须在2-50个字符之间")
    val code: String? = null,
    @field:Size(max = 255, message = "角色描述不能超过255个字符")
    val description: String? = null,
    val isSystem: Boolean? = null,
)

/** 分配权限（对标 AssignPermissionsDto，id 在 body 中） */
data class AssignPermissionsDto(
    @field:NotBlank(message = "角色ID不能为空")
    val id: String,
    val permissionIds: List<String>,
)

/** 角色响应（对标 RoleResponseDto） */
data class RoleResponseDto(
    val id: String,
    val name: String,
    val code: String,
    val description: String?,
    val isSystem: Boolean,
    val createdAt: LocalDateTime,
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
