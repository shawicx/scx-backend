package com.scx.backend.modules.userrole

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.UserRole
import com.scx.backend.repository.RoleRepository
import com.scx.backend.repository.UserRepository
import com.scx.backend.repository.UserRoleRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 用户-角色关联服务
 * 对标 scx-service: src/modules/user-role/user-role.service.ts
 */
@Service
class UserRoleService(
    private val userRoleRepository: UserRoleRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {
    private val logger = LoggerFactory.getLogger(UserRoleService::class.java)

    @Transactional
    fun create(userId: String, roleId: String): UserRole {
        if (!userRepository.existsById(userId)) {
            throw SystemException.dataNotFound("User with ID '$userId' not found")
        }
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw SystemException.resourceExists("User already has this role")
        }
        val userRole = UserRole(id = IdGenerator.nextId(), userId = userId, roleId = roleId)
        val saved = userRoleRepository.save(userRole)
        logger.info("User role assigned: User {} -> Role {}", userId, roleId)
        return saved
    }

    @Transactional
    fun createBulk(userId: String, roleIds: List<String>): List<UserRole> {
        if (!userRepository.existsById(userId)) {
            throw SystemException.dataNotFound("User with ID '$userId' not found")
        }
        val existingRoles = roleRepository.findAllById(roleIds)
        if (existingRoles.size != roleIds.distinct().size) {
            val foundIds = existingRoles.map { it.id }.toSet()
            val missingIds = roleIds.distinct().filter { it !in foundIds }
            throw SystemException.dataNotFound("Roles not found: ${missingIds.joinToString(", ")}")
        }
        val existingAssignments = userRoleRepository.findByUserIdAndRoleIdIn(userId, roleIds)
        val existingRoleIds = existingAssignments.map { it.roleId }.toSet()
        val newRoleIds = roleIds.distinct().filter { it !in existingRoleIds }
        if (newRoleIds.isEmpty()) {
            throw SystemException.resourceExists("User already has all specified roles")
        }
        val created = newRoleIds.map { roleId ->
            userRoleRepository.save(UserRole(id = IdGenerator.nextId(), userId = userId, roleId = roleId))
        }
        logger.info("Bulk user roles assigned: User {} -> {} roles", userId, newRoleIds.size)
        return created
    }

    fun findAll(page: Int = 1, limit: Int = 10): Map<String, Any> {
        val pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = userRoleRepository.findAll(pageable)
        return mapOf("list" to result.content, "total" to result.totalElements)
    }

    fun findByUserId(userId: String): List<UserRole> {
        if (!userRepository.existsById(userId)) {
            throw SystemException.dataNotFound("User with ID '$userId' not found")
        }
        return userRoleRepository.findByUserId(userId)
    }

    fun findByRoleId(roleId: String): List<UserRole> {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        return userRoleRepository.findByRoleId(roleId)
    }

    fun findByUserAndRole(userId: String, roleId: String): UserRole? =
        userRoleRepository.findByUserIdAndRoleId(userId, roleId)

    @Transactional
    fun delete(userId: String, roleId: String) {
        val userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
            ?: throw SystemException.dataNotFound(
                "User role assignment not found for user '$userId' and role '$roleId'",
            )
        userRoleRepository.delete(userRole)
        logger.info("User role removed: User {} -> Role {}", userId, roleId)
    }

    @Transactional
    fun deleteByUserId(userId: String) {
        if (!userRepository.existsById(userId)) {
            throw SystemException.dataNotFound("User with ID '$userId' not found")
        }
        val count = userRoleRepository.deleteByUserId(userId)
        logger.info("All user roles removed for user {}: {} assignments", userId, count)
    }

    @Transactional
    fun deleteByRoleId(roleId: String) {
        if (!roleRepository.existsById(roleId)) {
            throw SystemException.dataNotFound("Role with ID '$roleId' not found")
        }
        val count = userRoleRepository.deleteByRoleId(roleId)
        logger.info("All user roles removed for role {}: {} assignments", roleId, count)
    }

    fun count(): Long = userRoleRepository.count()

    fun countByUserId(userId: String): Long = userRoleRepository.findByUserId(userId).size.toLong()

    fun countByRoleId(roleId: String): Long = userRoleRepository.countByRoleId(roleId)
}
