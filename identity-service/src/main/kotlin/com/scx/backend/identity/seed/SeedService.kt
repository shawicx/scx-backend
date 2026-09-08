package com.scx.backend.identity.seed

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.rbac.entity.Permission
import com.scx.backend.rbac.entity.Role
import com.scx.backend.identity.entity.User
import com.scx.backend.identity.entity.UserPreferences
import com.scx.backend.identity.entity.UserRole
import com.scx.backend.rbac.repository.PermissionRepository
import com.scx.backend.rbac.repository.RoleRepository
import com.scx.backend.identity.repository.UserRepository
import com.scx.backend.identity.repository.UserRoleRepository
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
 *  4. 权限字典（6 菜单 + 12 按钮，仅定义不绑定角色）
 *
 * 幂等：已存在则跳过。
 */
@Component
class SeedService(
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val permissionRepository: PermissionRepository,
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

        // 4. 权限字典（仅定义，不绑定角色）
        seedPermissionDict()
    }

    /**
     * @description 初始化权限字典（6 个受控菜单 + 各页面按钮权限定义）
     *
     * 只定义权限行、不绑定任何角色：管理员经 isAdmin 直通全部菜单；
     * 普通角色由管理员在角色管理页手动分配。幂等：按 name 唯一约束判断已存在则跳过。
     * 仪表板与个人资料是账户级导航（前端固定渲染），不在此列。
     */
    private fun seedPermissionDict() {
        data class ButtonSeed(val action: String, val resource: String)
        data class MenuSeed(
            val name: String, val path: String, val icon: String, val sort: Int,
            val buttons: List<ButtonSeed>,
        )

        val dict = listOf(
            MenuSeed("用户管理", "/users", "users", 2, listOf(
                ButtonSeed("create", "user"),
                ButtonSeed("update", "user"),
                ButtonSeed("delete", "user"),
                ButtonSeed("assign-role", "user"),
            )),
            MenuSeed("角色管理", "/roles", "roles", 3, listOf(
                ButtonSeed("create", "role"),
                ButtonSeed("update", "role"),
                ButtonSeed("delete", "role"),
                ButtonSeed("assign-permissions", "role"),
            )),
            MenuSeed("权限管理", "/permissions", "permissions", 4, listOf(
                ButtonSeed("create", "permission"),
                ButtonSeed("update", "permission"),
                ButtonSeed("delete", "permission"),
            )),
            MenuSeed("文件管理", "/files", "files", 5, listOf(
                ButtonSeed("delete", "file"),
            )),
            MenuSeed("操作日志", "/logs/operations", "operation-logs", 6, emptyList()),
            MenuSeed("登录日志", "/logs/logins", "login-logs", 7, emptyList()),
        )

        dict.forEach { menuSeed ->
            val menu = permissionRepository.findByName(menuSeed.name) ?: run {
                permissionRepository.save(
                    Permission(
                        id = IdGenerator.nextId(),
                        name = menuSeed.name,
                        type = "MENU",
                        level = 1,
                        path = menuSeed.path,
                        icon = menuSeed.icon,
                        sort = menuSeed.sort,
                        visible = 1,
                        status = 1,
                        description = "系统菜单：${menuSeed.name}",
                    ),
                ).also { logger.info("已创建菜单权限: ${menuSeed.name}") }
            }
            menuSeed.buttons.forEach { buttonSeed ->
                val buttonName = "${menuSeed.name}-${buttonSeed.action}"
                if (permissionRepository.findByName(buttonName) == null) {
                    permissionRepository.save(
                        Permission(
                            id = IdGenerator.nextId(),
                            name = buttonName,
                            action = buttonSeed.action,
                            resource = buttonSeed.resource,
                            type = "BUTTON",
                            parentId = menu.id,
                            level = 2,
                            sort = 0,
                            visible = 1,
                            status = 1,
                            description = "按钮权限：${buttonSeed.resource}:${buttonSeed.action}",
                        ),
                    )
                }
            }
        }
    }

    /**
     * @description 构造默认的用户偏好设置（与 UserService.defaultPreferences 保持一致）
     * @returns UserPreferences 默认偏好
     */
    private fun defaultPreferences(): UserPreferences = UserPreferences(
        theme = "light",
        language = "zh-CN",
        timezone = "Asia/Shanghai",
        notifications = UserPreferences.NotificationPrefs(email = true, push = true, sms = false),
        privacy = UserPreferences.PrivacyPrefs(profileVisible = true, showEmail = false, showLastSeen = true),
    )
}
