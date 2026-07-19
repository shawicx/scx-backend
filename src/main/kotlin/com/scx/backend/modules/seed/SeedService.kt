package com.scx.backend.modules.seed

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.Role
import com.scx.backend.entity.User
import com.scx.backend.entity.UserRole
import com.scx.backend.repository.RoleRepository
import com.scx.backend.repository.UserRepository
import com.scx.backend.repository.UserRoleRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/**
 * 种子数据初始化
 *
 * 应用启动时自动创建：
 *  1. SUPER_ADMIN 角色（系统内置）
 *  2. 超级管理员用户（邮箱 scx-super-admin@system.internal，密码取 ADMIN_INITIAL_PASSWORD）
 *  3. 用户-角色关联
 *
 * 幂等：已存在则跳过。
 */
@Component
class SeedService(
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${admin.initial-password:changeme123}") private val initialPassword: String,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(SeedService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(12)

    companion object {
        private const val SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN"
        private const val SUPER_ADMIN_USER_NAME = "scx-super-admin"
        private const val SUPER_ADMIN_USER_EMAIL = "scx-super-admin@system.internal"
    }

    override fun run(args: ApplicationArguments) {
        try {
            seed()
        } catch (e: Exception) {
            logger.error("数据初始化失败", e)
        }
    }

    private fun seed() {
        // 1. 创建 SUPER_ADMIN 角色
        val superAdminRole = roleRepository.findByCode(SUPER_ADMIN_ROLE_CODE) ?: run {
            val role = Role(
                id = IdGenerator.nextId(),
                name = "超级管理员",
                code = SUPER_ADMIN_ROLE_CODE,
                description = "系统内置超级管理员角色，拥有所有权限",
                isSystem = true,
            )
            roleRepository.save(role).also { logger.info("已创建 SUPER_ADMIN 角色") }
        }

        // 2. 创建超级管理员用户
        val adminUser = userRepository.findByEmail(SUPER_ADMIN_USER_EMAIL) ?: run {
            val user = User(
                id = IdGenerator.nextId(),
                email = SUPER_ADMIN_USER_EMAIL,
                name = SUPER_ADMIN_USER_NAME,
                password = passwordEncoder.encode(initialPassword)!!,
                emailVerified = true,
                isActive = true,
                loginCount = 0,
                preferences = defaultPreferences(),
            )
            userRepository.save(user).also { logger.info("已创建默认超级管理员用户: $SUPER_ADMIN_USER_EMAIL") }
        }

        // 3. 关联用户与角色
        if (!userRoleRepository.existsByUserIdAndRoleId(adminUser.id, superAdminRole.id)) {
            userRoleRepository.save(
                UserRole(id = IdGenerator.nextId(), userId = adminUser.id, roleId = superAdminRole.id),
            )
            logger.info("已关联超级管理员用户与角色")
        }
    }

    private fun defaultPreferences() = objectMapper.readTree(
        """{"theme":"light","language":"zh-CN","timezone":"Asia/Shanghai","notifications":{"email":true,"push":true,"sms":false},"privacy":{"profileVisible":true,"showEmail":false,"showLastSeen":true}}""",
    )
}
