package com.scx.backend.repository

import com.scx.backend.entity.RolePermission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RolePermissionRepository : JpaRepository<RolePermission, String> {
    fun findByRoleId(roleId: String): List<RolePermission>
    fun findByPermissionId(permissionId: String): List<RolePermission>
    fun findByRoleIdAndPermissionId(roleId: String, permissionId: String): RolePermission?
    fun deleteByRoleId(roleId: String): Int
    fun existsByRoleIdAndPermissionId(roleId: String, permissionId: String): Boolean
    fun countByPermissionId(permissionId: String): Long
}
