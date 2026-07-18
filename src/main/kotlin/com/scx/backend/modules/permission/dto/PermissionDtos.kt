package com.scx.backend.modules.permission.dto

import com.scx.backend.entity.Permission
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 创建权限（对标 CreatePermissionDto） */
data class CreatePermissionDto(
    @field:NotBlank(message = "权限名称不能为空")
    @field:Size(min = 2, max = 100, message = "权限名称长度必须在2-100个字符之间")
    val name: String,
    @field:NotBlank(message = "权限类型不能为空")
    @field:Pattern(regexp = "MENU|BUTTON", message = "权限类型必须是MENU或BUTTON")
    val type: String,
    @field:Size(min = 2, max = 50, message = "操作动作长度必须在2-50个字符之间")
    val action: String? = null,
    @field:Size(min = 2, max = 100, message = "资源名称长度必须在2-100个字符之间")
    val resource: String? = null,
    val parentId: String? = null,
    @field:Size(max = 200, message = "路由路径不能超过200个字符")
    val path: String? = null,
    @field:Size(max = 100, message = "图标不能超过100个字符")
    val icon: String? = null,
    @field:Min(0, message = "排序号不能小于0")
    val sort: Int? = 0,
    @field:Min(0, message = "是否可见只能是0或1")
    @field:Max(1, message = "是否可见只能是0或1")
    val visible: Int? = 1,
    @field:Min(0, message = "状态只能是0或1")
    @field:Max(1, message = "状态只能是0或1")
    val status: Int? = 1,
    @field:Size(max = 255, message = "权限描述不能超过255个字符")
    val description: String? = null,
)

/** 更新权限（对标 UpdatePermissionDto，id 在 body） */
data class UpdatePermissionDto(
    @field:NotBlank(message = "权限ID不能为空")
    val id: String,
    @field:Size(min = 2, max = 100, message = "权限名称长度必须在2-100个字符之间")
    val name: String? = null,
    @field:Pattern(regexp = "MENU|BUTTON", message = "权限类型必须是MENU或BUTTON")
    val type: String? = null,
    @field:Size(min = 2, max = 50, message = "操作动作长度必须在2-50个字符之间")
    val action: String? = null,
    @field:Size(min = 2, max = 100, message = "资源名称长度必须在2-100个字符之间")
    val resource: String? = null,
    val parentId: String? = null,
    @field:Size(max = 200, message = "路由路径不能超过200个字符")
    val path: String? = null,
    @field:Size(max = 100, message = "图标不能超过100个字符")
    val icon: String? = null,
    @field:Min(0, message = "排序号不能小于0")
    val sort: Int? = null,
    @field:Min(0, message = "是否可见只能是0或1")
    @field:Max(1, message = "是否可见只能是0或1")
    val visible: Int? = null,
    @field:Min(0, message = "状态只能是0或1")
    @field:Max(1, message = "状态只能是0或1")
    val status: Int? = null,
    @field:Size(max = 255, message = "权限描述不能超过255个字符")
    val description: String? = null,
)

/** 权限查询（对标 PermissionQueryDto） */
data class PermissionQueryDto(
    val page: Int = 1,
    val limit: Int = 10,
    val search: String? = null,
    val action: String? = null,
    val resource: String? = null,
    @field:Pattern(regexp = "MENU|BUTTON", message = "类型筛选只能是MENU或BUTTON")
    val type: String? = null,
    val parentId: String? = null,
    val level: Int? = null,
)

/** 权限响应（对标 PermissionResponseDto） */
data class PermissionResponseDto(
    val id: String,
    val name: String,
    val type: String,
    val action: String?,
    val resource: String?,
    val parentId: String?,
    val level: Int,
    val path: String?,
    val icon: String?,
    val sort: Int,
    val visible: Int,
    val status: Int,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(p: Permission): PermissionResponseDto = PermissionResponseDto(
            id = p.id, name = p.name, type = p.type, action = p.action,
            resource = p.resource, parentId = p.parentId, level = p.level,
            path = p.path, icon = p.icon, sort = p.sort, visible = p.visible,
            status = p.status, description = p.description,
            createdAt = p.createdAt, updatedAt = p.updatedAt,
        )
    }
}

/** 权限树响应（含 children） */
data class PermissionTreeResponseDto(
    val id: String,
    val name: String,
    val type: String,
    val action: String?,
    val resource: String?,
    val parentId: String?,
    val level: Int,
    val path: String?,
    val icon: String?,
    val sort: Int,
    val visible: Int,
    val status: Int,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val children: List<PermissionTreeResponseDto>?,
)

/** 菜单树响应（精简，仅菜单字段） */
data class PermissionMenuTreeDto(
    val id: String,
    val name: String,
    val path: String?,
    val icon: String?,
    val children: List<PermissionMenuTreeDto>?,
)
