package com.scx.backend.rbac.rolepermission.dto

import com.scx.backend.rbac.entity.RolePermission
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * @description 角色-权限关联信息响应
 *
 * @property id 关联记录 ID
 * @property roleId 角色 ID
 * @property permissionId 权限 ID
 * @property createdAt 创建时间
 *
 * @example RolePermissionResponseDto("01J...", "01AROLE...", "01APERM...", 2026-09-05T10:00:00)
 */
@Schema(description = "角色-权限关联信息响应")
data class RolePermissionResponseDto(
    @Schema(description = "关联记录 ID")
    val id: String,

    @Schema(description = "角色 ID")
    val roleId: String,

    @Schema(description = "权限 ID")
    val permissionId: String,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 从角色-权限关联实体构造响应 DTO
         * @param rp 角色-权限关联实体
         * @returns RolePermissionResponseDto 关联信息响应
         */
        fun from(rp: RolePermission): RolePermissionResponseDto = RolePermissionResponseDto(
            id = rp.id,
            roleId = rp.roleId,
            permissionId = rp.permissionId,
            createdAt = rp.createdAt,
        )
    }
}

/**
 * @description 角色-权限关联列表响应（分页）
 *
 * @property list 关联列表
 * @property total 总数
 * @property page 当前页码
 * @property limit 每页条数
 *
 * @example RolePermissionListResponseDto(list = emptyList(), total = 0, page = 1, limit = 10)
 */
@Schema(description = "角色-权限关联列表响应")
data class RolePermissionListResponseDto(
    @Schema(description = "关联列表")
    val list: List<RolePermissionResponseDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)
