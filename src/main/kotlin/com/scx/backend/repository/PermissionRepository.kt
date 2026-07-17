package com.scx.backend.repository

import com.scx.backend.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : JpaRepository<Permission, String> {
    fun findByName(name: String): Permission?
    fun findByParentId(parentId: String): List<Permission>
    fun findByLevel(level: Int): List<Permission>

    /** 查询角色集合关联的权限（去重） */
    @Query(
        "SELECT DISTINCT p FROM Permission p JOIN RolePermission rp ON rp.permissionId = p.id " +
            "WHERE rp.roleId IN :roleIds",
    )
    fun findPermissionsByRoleIds(@Param("roleIds") roleIds: List<String>): List<Permission>

    /** 检查角色集合是否拥有指定 action+resource 的权限 */
    @Query(
        "SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Permission p " +
            "JOIN RolePermission rp ON rp.permissionId = p.id " +
            "WHERE rp.roleId IN :roleIds AND p.action = :action AND p.resource = :resource",
    )
    fun existsPermissionByRoleIdsAndActionAndResource(
        @Param("roleIds") roleIds: List<String>,
        @Param("action") action: String,
        @Param("resource") resource: String,
    ): Boolean
}
