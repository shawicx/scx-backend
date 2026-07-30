package com.scx.backend.modules.permission.dto

import com.scx.backend.entity.Permission
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 创建权限请求 */
@Schema(description = "创建权限请求")
data class CreatePermissionDto(
    @Schema(description = "权限名称（2-100 字符）", required = true)
    @field:NotBlank(message = "权限名称不能为空")
    @field:Size(min = 2, max = 100, message = "权限名称长度必须在2-100个字符之间")
    val name: String,

    @Schema(description = "权限类型（MENU 菜单 / BUTTON 按钮）", required = true)
    @field:NotBlank(message = "权限类型不能为空")
    @field:Pattern(regexp = "MENU|BUTTON", message = "权限类型必须是MENU或BUTTON")
    val type: String,

    @Schema(description = "操作动作（按钮类型，2-50 字符）")
    @field:Size(min = 2, max = 50, message = "操作动作长度必须在2-50个字符之间")
    val action: String? = null,

    @Schema(description = "资源名称（按钮类型，2-100 字符）")
    @field:Size(min = 2, max = 100, message = "资源名称长度必须在2-100个字符之间")
    val resource: String? = null,

    @Schema(description = "父权限 ID（自引用树）")
    val parentId: String? = null,

    @Schema(description = "路由路径（菜单类型，最长 200 字符）")
    @field:Size(max = 200, message = "路由路径不能超过200个字符")
    val path: String? = null,

    @Schema(description = "图标（菜单类型，最长 100 字符）")
    @field:Size(max = 100, message = "图标不能超过100个字符")
    val icon: String? = null,

    @Schema(description = "排序号（升序，≥0）")
    @field:Min(0, message = "排序号不能小于0")
    val sort: Int? = 0,

    @Schema(description = "是否可见（0 隐藏 / 1 显示）")
    @field:Min(0, message = "是否可见只能是0或1")
    @field:Max(1, message = "是否可见只能是0或1")
    val visible: Int? = 1,

    @Schema(description = "状态（0 停用 / 1 启用）")
    @field:Min(0, message = "状态只能是0或1")
    @field:Max(1, message = "状态只能是0或1")
    val status: Int? = 1,

    @Schema(description = "权限描述（最长 255 字符）")
    @field:Size(max = 255, message = "权限描述不能超过255个字符")
    val description: String? = null,
)

/** 更新权限请求（ID 在 body 中） */
@Schema(description = "更新权限请求")
data class UpdatePermissionDto(
    @Schema(description = "权限 ID", required = true)
    @field:NotBlank(message = "权限ID不能为空")
    val id: String,

    @Schema(description = "权限名称（2-100 字符）")
    @field:Size(min = 2, max = 100, message = "权限名称长度必须在2-100个字符之间")
    val name: String? = null,

    @Schema(description = "权限类型（MENU / BUTTON）")
    @field:Pattern(regexp = "MENU|BUTTON", message = "权限类型必须是MENU或BUTTON")
    val type: String? = null,

    @Schema(description = "操作动作（2-50 字符）")
    @field:Size(min = 2, max = 50, message = "操作动作长度必须在2-50个字符之间")
    val action: String? = null,

    @Schema(description = "资源名称（2-100 字符）")
    @field:Size(min = 2, max = 100, message = "资源名称长度必须在2-100个字符之间")
    val resource: String? = null,

    @Schema(description = "父权限 ID")
    val parentId: String? = null,

    @Schema(description = "路由路径（最长 200 字符）")
    @field:Size(max = 200, message = "路由路径不能超过200个字符")
    val path: String? = null,

    @Schema(description = "图标（最长 100 字符）")
    @field:Size(max = 100, message = "图标不能超过100个字符")
    val icon: String? = null,

    @Schema(description = "排序号（≥0）")
    @field:Min(0, message = "排序号不能小于0")
    val sort: Int? = null,

    @Schema(description = "是否可见（0 / 1）")
    @field:Min(0, message = "是否可见只能是0或1")
    @field:Max(1, message = "是否可见只能是0或1")
    val visible: Int? = null,

    @Schema(description = "状态（0 / 1）")
    @field:Min(0, message = "状态只能是0或1")
    @field:Max(1, message = "状态只能是0或1")
    val status: Int? = null,

    @Schema(description = "权限描述（最长 255 字符）")
    @field:Size(max = 255, message = "权限描述不能超过255个字符")
    val description: String? = null,
)

/** 权限查询参数 */
@Schema(description = "权限查询参数")
data class PermissionQueryDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字")
    val search: String? = null,

    @Schema(description = "按动作过滤")
    val action: String? = null,

    @Schema(description = "按资源过滤")
    val resource: String? = null,

    @Schema(description = "按类型过滤（MENU / BUTTON）")
    @field:Pattern(regexp = "MENU|BUTTON", message = "类型筛选只能是MENU或BUTTON")
    val type: String? = null,

    @Schema(description = "按父节点过滤")
    val parentId: String? = null,

    @Schema(description = "按层级过滤")
    val level: Int? = null,
)

/** 权限信息响应 */
@Schema(description = "权限信息响应")
data class PermissionResponseDto(
    @Schema(description = "权限 ID")
    val id: String,

    @Schema(description = "权限名称")
    val name: String,

    @Schema(description = "权限类型（MENU / BUTTON）")
    val type: String,

    @Schema(description = "操作动作")
    val action: String?,

    @Schema(description = "资源名称")
    val resource: String?,

    @Schema(description = "父权限 ID")
    val parentId: String?,

    @Schema(description = "层级")
    val level: Int,

    @Schema(description = "路由路径")
    val path: String?,

    @Schema(description = "图标")
    val icon: String?,

    @Schema(description = "排序号")
    val sort: Int,

    @Schema(description = "是否可见（0 / 1）")
    val visible: Int,

    @Schema(description = "状态（0 / 1）")
    val status: Int,

    @Schema(description = "权限描述")
    val description: String?,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "更新时间")
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

/** 权限树节点响应（含 children） */
@Schema(description = "权限树节点响应")
data class PermissionTreeResponseDto(
    @Schema(description = "权限 ID")
    val id: String,

    @Schema(description = "权限名称")
    val name: String,

    @Schema(description = "权限类型（MENU / BUTTON）")
    val type: String,

    @Schema(description = "操作动作")
    val action: String?,

    @Schema(description = "资源名称")
    val resource: String?,

    @Schema(description = "父权限 ID")
    val parentId: String?,

    @Schema(description = "层级")
    val level: Int,

    @Schema(description = "路由路径")
    val path: String?,

    @Schema(description = "图标")
    val icon: String?,

    @Schema(description = "排序号")
    val sort: Int,

    @Schema(description = "是否可见（0 / 1）")
    val visible: Int,

    @Schema(description = "状态（0 / 1）")
    val status: Int,

    @Schema(description = "权限描述")
    val description: String?,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "更新时间")
    val updatedAt: LocalDateTime,

    @Schema(description = "子节点列表")
    val children: List<PermissionTreeResponseDto>?,
)

/** 菜单树节点响应（精简，仅菜单字段） */
@Schema(description = "菜单树节点响应")
data class PermissionMenuTreeDto(
    @Schema(description = "菜单 ID")
    val id: String,

    @Schema(description = "菜单名称")
    val name: String,

    @Schema(description = "路由路径")
    val path: String?,

    @Schema(description = "图标")
    val icon: String?,

    @Schema(description = "子菜单列表")
    val children: List<PermissionMenuTreeDto>?,
)
