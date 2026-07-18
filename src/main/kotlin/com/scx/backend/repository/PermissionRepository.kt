package com.scx.backend.repository

import com.scx.backend.entity.Permission
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : JpaRepository<Permission, String>, JpaSpecificationExecutor<Permission> {
    fun findByName(name: String): Permission?
    fun findByParentId(parentId: String): List<Permission>
    fun findByLevel(level: Int): List<Permission>
    fun findByAction(action: String): List<Permission>
    fun findByResource(resource: String): List<Permission>
    fun findByLevelAndStatus(level: Int, status: Int): List<Permission>

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

    /** 所有唯一的 action（非空） */
    @Query("SELECT DISTINCT p.action FROM Permission p WHERE p.action IS NOT NULL")
    fun findDistinctActions(): List<String>

    /** 所有唯一的 resource（非空） */
    @Query("SELECT DISTINCT p.resource FROM Permission p WHERE p.resource IS NOT NULL")
    fun findDistinctResources(): List<String>

    /** 关键词搜索（name/action/resource/description，不区分大小写） */
    @Query(
        "SELECT p FROM Permission p WHERE " +
            "LOWER(p.name) LIKE %:kw% OR LOWER(p.action) LIKE %:kw% OR " +
            "LOWER(p.resource) LIKE %:kw% OR LOWER(p.description) LIKE %:kw%",
    )
    fun searchByKeyword(@Param("kw") keyword: String, pageable: Pageable): List<Permission>

    /** 菜单树节点（仅可见且启用的 MENU，按 sort 升序） */
    @Query("SELECT p FROM Permission p WHERE p.type = 'MENU' AND p.status = 1 AND p.visible = 1 ORDER BY p.sort ASC, p.createdAt DESC")
    fun findMenuTreeNodes(): List<Permission>

    /** 菜单下的按钮（仅启用） */
    @Query("SELECT p FROM Permission p WHERE p.parentId = :menuId AND p.type = 'BUTTON' AND p.status = 1 ORDER BY p.sort ASC, p.createdAt DESC")
    fun findButtonsByMenuId(@Param("menuId") menuId: String): List<Permission>
}
