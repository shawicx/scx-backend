package com.scx.backend.modules.rolepermission

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.RolePermission
import com.scx.backend.repository.PermissionRepository
import com.scx.backend.repository.RolePermissionRepository
import com.scx.backend.repository.RoleRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 角色-权限关联服务
 * 对标 scx-service: src/modules/role-permission/role-permission.service.ts
 */
@Service
class RolePermissionService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val entityManager: EntityManager,
) {
    private val logger = LoggerFactory.getLogger(RolePermissionService::class.java)

    @Transactional
    fun create(roleId: String, permissionId: String): RolePermission {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        if (!permissionRepository.existsById(permissionId)) {
            throw SystemException.dataNotFound("Permission with ID '$permissionId' not found")
        }
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw SystemException.resourceExists("Role already has this permission")
        }
        val saved = rolePermissionRepository.save(
            RolePermission(id = IdGenerator.nextId(), roleId = roleId, permissionId = permissionId),
        )
        logger.info("Role permission assigned: Role {} -> Permission {}", roleId, permissionId)
        return saved
    }

    @Transactional
    fun createBulk(roleId: String, permissionIds: List<String>): List<RolePermission> {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        val existingPerms = permissionRepository.findAllById(permissionIds)
        if (existingPerms.size != permissionIds.distinct().size) {
            val foundIds = existingPerms.map { it.id }.toSet()
            val missingIds = permissionIds.distinct().filter { it !in foundIds }
            throw SystemException.dataNotFound("Permissions not found: ${missingIds.joinToString(", ")}")
        }
        val existing = rolePermissionRepository.findByRoleIdAndPermissionIdIn(roleId, permissionIds)
        val existingIds = existing.map { it.permissionId }.toSet()
        val newIds = permissionIds.distinct().filter { it !in existingIds }
        if (newIds.isEmpty()) {
            throw SystemException.resourceExists("Role already has all specified permissions")
        }
        val created = newIds.map { permId ->
            rolePermissionRepository.save(
                RolePermission(id = IdGenerator.nextId(), roleId = roleId, permissionId = permId),
            )
        }
        logger.info("Bulk role permissions assigned: Role {} -> {} permissions", roleId, newIds.size)
        return created
    }

    /**
     * 替换角色的全部权限（覆盖式）
     */
    @Transactional
    fun replacePermissions(roleId: String, permissionIds: List<String>): List<RolePermission> {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        if (permissionIds.isNotEmpty()) {
            val existingPerms = permissionRepository.findAllById(permissionIds.distinct())
            if (existingPerms.size != permissionIds.distinct().size) {
                val foundIds = existingPerms.map { it.id }.toSet()
                val missingIds = permissionIds.distinct().filter { it !in foundIds }
                throw SystemException.dataNotFound("Permissions not found: ${missingIds.joinToString(", ")}")
            }
        }
        // 用原生 SQL 执行删除+插入，绕过 Hibernate 的 insert-before-delete 执行顺序问题
        // （JPA 批量 delete 会排在 insert 之后执行，导致新插入记录被一并删除）
        entityManager.createNativeQuery("DELETE FROM role_permissions WHERE \"roleId\" = :roleId")
            .setParameter("roleId", roleId)
            .executeUpdate()
        val created = if (permissionIds.isNotEmpty()) {
            permissionIds.distinct().map { permId ->
                RolePermission(id = IdGenerator.nextId(), roleId = roleId, permissionId = permId)
            }.also { batch ->
                // 批量插入
                batch.forEach { rp ->
                    entityManager.createNativeQuery(
                        "INSERT INTO role_permissions (id, \"roleId\", \"permissionId\", \"createdAt\") VALUES (:id, :rid, :pid, :now)",
                    )
                        .setParameter("id", rp.id)
                        .setParameter("rid", rp.roleId)
                        .setParameter("pid", rp.permissionId)
                        .setParameter("now", java.time.LocalDateTime.now())
                        .executeUpdate()
                }
            }
        } else {
            emptyList()
        }
        logger.info("Role permissions replaced: Role {} -> {} permissions", roleId, permissionIds.size)
        return created
    }

    fun findAll(page: Int = 1, limit: Int = 10): Map<String, Any> {
        val pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = rolePermissionRepository.findAll(pageable)
        return mapOf("list" to result.content, "total" to result.totalElements)
    }

    fun findByRoleId(roleId: String): List<RolePermission> {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        return rolePermissionRepository.findByRoleId(roleId)
    }

    fun findByPermissionId(permissionId: String): List<RolePermission> {
        if (!permissionRepository.existsById(permissionId)) {
            throw SystemException.dataNotFound("Permission with ID '$permissionId' not found")
        }
        return rolePermissionRepository.findByPermissionId(permissionId)
    }

    fun findByRoleAndPermission(roleId: String, permissionId: String): RolePermission? =
        rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)

    @Transactional
    fun delete(roleId: String, permissionId: String) {
        val rp = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
            ?: throw SystemException.dataNotFound(
                "Role permission assignment not found for role '$roleId' and permission '$permissionId'",
            )
        rolePermissionRepository.delete(rp)
        logger.info("Role permission removed: Role {} -> Permission {}", roleId, permissionId)
    }

    @Transactional
    fun deleteByRoleId(roleId: String) {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        val count = rolePermissionRepository.deleteByRoleId(roleId)
        logger.info("All role permissions removed for role {}: {} assignments", roleId, count)
    }

    @Transactional
    fun deleteByPermissionId(permissionId: String) {
        if (!permissionRepository.existsById(permissionId)) {
            throw SystemException.dataNotFound("Permission with ID '$permissionId' not found")
        }
        val rps = rolePermissionRepository.findByPermissionId(permissionId)
        rolePermissionRepository.deleteAll(rps)
        logger.info("All role permissions removed for permission {}: {} assignments", permissionId, rps.size)
    }

    fun count(): Long = rolePermissionRepository.count()

    fun countByRoleId(roleId: String): Long = rolePermissionRepository.findByRoleId(roleId).size.toLong()

    fun countByPermissionId(permissionId: String): Long = rolePermissionRepository.countByPermissionId(permissionId)

    fun hasPermission(roleId: String, permissionId: String): Boolean =
        rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)

    fun getPermissionsByRole(roleId: String): List<String> =
        rolePermissionRepository.findByRoleId(roleId).map { it.permissionId }
}
