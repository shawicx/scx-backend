package com.scx.backend.modules.role

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.Permission
import com.scx.backend.entity.Role
import com.scx.backend.modules.role.dto.AssignPermissionsDto
import com.scx.backend.modules.role.dto.CreateRoleDto
import com.scx.backend.modules.role.dto.RoleResponseDto
import com.scx.backend.modules.role.dto.UpdateRoleDto
import com.scx.backend.modules.rolepermission.RolePermissionService
import com.scx.backend.repository.PermissionRepository
import com.scx.backend.repository.RoleRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 角色服务
 * 对标 scx-service: src/modules/role/role.service.ts
 */
@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionService: RolePermissionService,
) {
    private val logger = LoggerFactory.getLogger(RoleService::class.java)

    @Transactional
    fun create(dto: CreateRoleDto): RoleResponseDto {
        // 检查 name/code 唯一
        roleRepository.findByName(dto.name)?.let { throw SystemException.resourceExists("Role with name '${dto.name}' already exists") }
        roleRepository.findByCode(dto.code)?.let { throw SystemException.resourceExists("Role with code '${dto.code}' already exists") }

        val role = Role(
            id = IdGenerator.nextId(),
            name = dto.name,
            code = dto.code,
            description = dto.description,
            isSystem = dto.isSystem ?: false,
        )
        val saved = roleRepository.save(role)
        logger.info("Role created: {} ({})", saved.name, saved.code)
        return RoleResponseDto.from(saved)
    }

    fun findAll(page: Int = 1, limit: Int = 10): Map<String, Any> {
        val pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = roleRepository.findAll(pageable)
        return mapOf(
            "list" to result.content.map { RoleResponseDto.from(it) },
            "total" to result.totalElements,
        )
    }

    fun findById(id: String): RoleResponseDto {
        val role = roleRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Role with ID '$id' not found")
        }
        return RoleResponseDto.from(role)
    }

    fun findByCode(code: String): RoleResponseDto {
        val role = roleRepository.findByCode(code)
            ?: throw SystemException.dataNotFound("Role with code '$code' not found")
        return RoleResponseDto.from(role)
    }

    @Transactional
    fun update(id: String, dto: UpdateRoleDto): RoleResponseDto {
        val role = roleRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Role with ID '$id' not found")
        }
        // 系统内置角色不可修改
        if (role.isSystem) {
            throw SystemException.businessRuleViolation("Cannot modify system roles")
        }
        // 冲突检查
        dto.name?.let { newName ->
            if (newName != role.name) {
                roleRepository.findByName(newName)?.let { existing ->
                    if (existing.id != id) throw SystemException.resourceExists("Role with name '$newName' already exists")
                }
            }
        }
        dto.code?.let { newCode ->
            if (newCode != role.code) {
                roleRepository.findByCode(newCode)?.let { existing ->
                    if (existing.id != id) throw SystemException.resourceExists("Role with code '$newCode' already exists")
                }
            }
        }
        dto.name?.let { role.name = it }
        dto.code?.let { role.code = it }
        dto.description?.let { role.description = it }
        dto.isSystem?.let { role.isSystem = it }
        val updated = roleRepository.save(role)
        logger.info("Role updated: {} ({})", updated.name, updated.code)
        return RoleResponseDto.from(updated)
    }

    @Transactional
    fun delete(id: String) {
        val role = roleRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Role with ID '$id' not found")
        }
        if (role.isSystem) {
            throw SystemException.businessRuleViolation("Cannot delete system roles")
        }
        roleRepository.delete(role)
        logger.info("Role deleted: {} ({})", role.name, role.code)
    }

    /**
     * 为角色分配权限（覆盖式）
     */
    @Transactional
    fun assignPermissions(roleId: String, dto: AssignPermissionsDto) {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        // 校验权限是否存在
        if (dto.permissionIds.isNotEmpty()) {
            val perms = permissionRepository.findAllById(dto.permissionIds.distinct())
            if (perms.size != dto.permissionIds.distinct().size) {
                val foundIds = perms.map { it.id }.toSet()
                val missing = dto.permissionIds.distinct().filter { it !in foundIds }
                throw SystemException.dataNotFound("Permissions not found: ${missing.joinToString(", ")}")
            }
        }
        rolePermissionService.replacePermissions(roleId, dto.permissionIds.distinct())
        logger.info("Permissions assigned to role {}: {} permissions", roleId, dto.permissionIds.size)
    }

    /**
     * 获取角色的权限列表
     */
    fun getRolePermissions(roleId: String): List<Map<String, Any?>> {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        val permissionIds = rolePermissionService.getPermissionsByRole(roleId)
        if (permissionIds.isEmpty()) return emptyList()
        return permissionRepository.findAllById(permissionIds).map { it.toMap() }
    }

    /**
     * 移除角色的单个权限
     */
    @Transactional
    fun removePermission(roleId: String, permissionId: String) {
        rolePermissionService.delete(roleId, permissionId)
        logger.info("Permission removed from role: {} - {}", roleId, permissionId)
    }

    private fun Permission.toMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "type" to type, "action" to action,
        "resource" to resource, "description" to description, "level" to level,
        "path" to path, "icon" to icon,
    )
}
