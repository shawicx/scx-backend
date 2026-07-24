package com.scx.backend.modules.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.constants.CacheKeys
import com.scx.backend.common.constants.TtlConstants
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.CryptoUtil
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.User
import com.scx.backend.entity.UserPreferences
import com.scx.backend.entity.UserRole
import com.scx.backend.modules.auth.AuthService
import com.scx.backend.modules.auth.EncryptionKey
import com.scx.backend.modules.auth.TokenPair
import com.scx.backend.modules.cache.CacheService
import com.scx.backend.modules.mail.MailService
import com.scx.backend.modules.user.dto.AssignRoleDto
import com.scx.backend.modules.user.dto.AssignRolesDto
import com.scx.backend.modules.user.dto.CreateUserDto
import com.scx.backend.modules.user.dto.DeleteUsersDto
import com.scx.backend.modules.user.dto.LoginResponseDto
import com.scx.backend.modules.user.dto.LoginUserDto
import com.scx.backend.modules.user.dto.LoginWithPasswordDto
import com.scx.backend.modules.user.dto.QueryUsersDto
import com.scx.backend.modules.user.dto.RegisterUserDto
import com.scx.backend.modules.user.dto.ToggleUserStatusDto
import com.scx.backend.modules.user.dto.UserListResponseDto
import com.scx.backend.modules.user.dto.UserListItemDto
import com.scx.backend.modules.user.dto.UserResponseDto
import com.scx.backend.modules.user.dto.UserRoleResponseDto
import com.scx.backend.repository.PermissionRepository
import com.scx.backend.repository.RoleRepository
import com.scx.backend.repository.UserRepository
import com.scx.backend.repository.UserRoleRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 用户服务
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val permissionRepository: PermissionRepository,
    private val cacheService: CacheService,
    private val mailService: MailService,
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(12)

    // ============ 注册 / 登录 ============

    @Transactional
    fun register(dto: RegisterUserDto, clientIp: String?): UserResponseDto {
        if (userRepository.existsByEmail(dto.email)) {
            throw SystemException.emailExists()
        }
        if (!validateEmailCode(dto.email, dto.emailVerificationCode)) {
            throw SystemException.invalidVerificationCode()
        }
        val user = User(
            id = IdGenerator.nextId(),
            email = dto.email,
            name = dto.name,
            password = passwordEncoder.encode(dto.password)!!,
            emailVerified = true,
            preferences = defaultPreferences(),
            lastLoginIp = clientIp,
            lastLoginAt = LocalDateTime.now(),
            loginCount = 1,
        )
        val saved = userRepository.save(user)
        try {
            mailService.sendWelcomeEmail(saved.email, saved.name)
        } catch (e: Exception) {
            logger.warn("欢迎邮件发送失败（忽略）: ${e.message}")
        }
        return UserResponseDto.from(saved)
    }

    @Transactional
    fun loginWithEmailCode(dto: LoginUserDto, clientIp: String?): LoginResponseDto {
        val user = userRepository.findByEmail(dto.email)
            ?: throw SystemException.invalidCredentials("邮箱不存在")
        if (!user.isActive) throw SystemException.accountDisabled()
        if (!validateLoginCode(dto.email, dto.emailVerificationCode)) {
            throw SystemException.invalidVerificationCode()
        }
        updateLoginInfo(user.id, clientIp)
        val updated = userRepository.findById(user.id).orElseThrow()
        val access = authService.generateAccessToken(updated.id, updated.email)
        val refresh = authService.generateRefreshToken(updated.id, updated.email)
        return LoginResponseDto.from(updated, access, refresh)
    }

    @Transactional
    fun loginWithPassword(dto: LoginWithPasswordDto, clientIp: String?): LoginResponseDto {
        if (dto.keyId.isBlank()) {
            throw SystemException.invalidParameter("密码必须加密传输，请先获取加密密钥")
        }
        val encryptionKey = authService.getEncryptionKey(dto.keyId)
            ?: throw SystemException.keyExpired()
        val decryptedPassword = try {
            CryptoUtil.decrypt(dto.password, encryptionKey)
        } catch (e: Exception) {
            throw SystemException.decryptionFailed()
        }
        val user = userRepository.findByEmail(dto.email)
            ?: throw SystemException.invalidCredentials("邮箱或密码错误")
        if (!user.isActive) throw SystemException.accountDisabled()
        if (!passwordEncoder.matches(decryptedPassword, user.password)) {
            throw SystemException.invalidCredentials("邮箱或密码错误")
        }
        updateLoginInfo(user.id, clientIp)
        val updated = userRepository.findById(user.id).orElseThrow()
        val access = authService.generateAccessToken(updated.id, updated.email)
        val refresh = authService.generateRefreshToken(updated.id, updated.email)
        return LoginResponseDto.from(updated, access, refresh)
    }

    fun logout(userId: String) {
        authService.logout(userId)
    }

    fun refreshTokens(refreshToken: String): TokenPair? = authService.refreshTokens(refreshToken)

    fun getEncryptionKey(): EncryptionKey = authService.generateEncryptionKey()

    // ============ 验证码 ============

    fun sendEmailVerificationCode(email: String): Boolean {
        if (userRepository.existsByEmail(email)) {
            throw SystemException.emailExists()
        }
        val result = mailService.sendVerificationCode(email)
        if (!result.success || result.code == null) {
            throw SystemException.operationFailed("验证码发送失败，请稍后重试")
        }
        cacheService.setWithMilliseconds(CacheKeys.emailVerification(email), result.code, TtlConstants.EMAIL_VERIFICATION_TTL_MS)
        return true
    }

    fun sendLoginVerificationCode(email: String): Boolean {
        if (!userRepository.existsByEmail(email)) {
            throw SystemException.dataNotFound("用户不存在")
        }
        val result = mailService.sendVerificationCode(email)
        if (!result.success || result.code == null) {
            throw SystemException.operationFailed("验证码发送失败，请稍后重试")
        }
        cacheService.setWithMilliseconds(CacheKeys.loginVerification(email), result.code, TtlConstants.LOGIN_VERIFICATION_TTL_MS)
        return true
    }

    // ============ 查询 ============

    fun findById(id: String): UserResponseDto? {
        val user = userRepository.findById(id).orElse(null) ?: return null
        return UserResponseDto.from(user)
    }

    fun findByEmail(email: String): User? = userRepository.findByEmail(email)

    @Transactional
    fun updateLoginInfo(userId: String, clientIp: String?) {
        val user = userRepository.findById(userId).orElseThrow()
        user.lastLoginIp = clientIp
        user.lastLoginAt = LocalDateTime.now()
        user.loginCount = user.loginCount + 1
        userRepository.save(user)
    }

    @Transactional
    fun updatePreferences(userId: String, preferences: Map<String, Any?>) {
        val user = userRepository.findById(userId).orElseThrow {
            SystemException.dataNotFound("用户不存在")
        }
        val merged = mergePreferences(user.preferences, preferences)
        user.preferences = merged
        userRepository.save(user)
    }

    fun queryUsers(dto: QueryUsersDto): UserListResponseDto {
        val spec = Specification<User> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            // 仅查未软删除的用户
            predicates.add(cb.isNull(root.get<Any>("deletedAt")))
            // 搜索（email/name，不区分大小写）
            if (!dto.search.isNullOrBlank()) {
                val pattern = "%${dto.search.lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                    ),
                )
            }
            // 启用状态
            if (dto.isActive != null) {
                predicates.add(cb.equal(root.get<Boolean>("isActive"), dto.isActive))
            }
            cb.and(*predicates.toTypedArray())
        }
        val direction = if (dto.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val sortField = dto.sortBy.takeIf { it in listOf("createdAt", "updatedAt", "name", "email") } ?: "createdAt"
        val pageable = PageRequest.of(dto.page - 1, dto.limit, Sort.by(direction, sortField))
        val page = userRepository.findAll(spec, pageable)
        return UserListResponseDto(
            list = page.content.map { u ->
                UserListItemDto(u.id, u.email, u.name, u.emailVerified, u.isActive, u.lastLoginAt, u.loginCount, u.createdAt)
            },
            total = page.totalElements,
            page = dto.page,
            limit = dto.limit,
        )
    }

    // ============ 角色分配 ============

    @Transactional
    fun assignRole(userId: String, dto: AssignRoleDto): UserRoleResponseDto {
        if (!userRepository.existsById(userId)) throw SystemException.dataNotFound("用户不存在")
        if (!roleRepository.existsById(dto.roleId)) throw SystemException.dataNotFound("角色不存在")
        if (userRoleRepository.existsByUserIdAndRoleId(userId, dto.roleId)) {
            throw SystemException.resourceExists("用户已拥有该角色")
        }
        val ur = userRoleRepository.save(UserRole(id = IdGenerator.nextId(), userId = userId, roleId = dto.roleId))
        return UserRoleResponseDto(ur.id, ur.userId, ur.roleId, ur.createdAt)
    }

    @Transactional
    fun assignRoles(userId: String, dto: AssignRolesDto): List<UserRoleResponseDto> {
        if (!userRepository.existsById(userId)) throw SystemException.dataNotFound("用户不存在")
        val roles = roleRepository.findAllById(dto.roleIds.distinct())
        if (roles.size != dto.roleIds.distinct().size) {
            val found = roles.map { it.id }.toSet()
            val missing = dto.roleIds.distinct().filter { it !in found }
            throw SystemException.dataNotFound("角色不存在: ${missing.joinToString(", ")}")
        }
        val existing = userRoleRepository.findByUserIdAndRoleIdIn(userId, dto.roleIds)
        val existingIds = existing.map { it.roleId }.toSet()
        val newIds = dto.roleIds.distinct().filter { it !in existingIds }
        if (newIds.isEmpty()) throw SystemException.resourceExists("用户已拥有所有指定角色")
        val created = newIds.map { roleId ->
            userRoleRepository.save(UserRole(id = IdGenerator.nextId(), userId = userId, roleId = roleId))
        }
        return created.map { UserRoleResponseDto(it.id, it.userId, it.roleId, it.createdAt) }
    }

    @Transactional
    fun removeRole(userId: String, roleId: String) {
        val ur = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
            ?: throw SystemException.dataNotFound("用户角色关系不存在")
        userRoleRepository.delete(ur)
    }

    fun getUserRoles(userId: String): List<Map<String, Any?>> {
        if (!userRepository.existsById(userId)) throw SystemException.dataNotFound("用户不存在")
        return userRoleRepository.findByUserId(userId).mapNotNull { ur ->
            roleRepository.findById(ur.roleId).orElse(null)?.let { role ->
                mapOf("id" to role.id, "name" to role.name, "code" to role.code, "description" to role.description)
            }
        }
    }

    fun hasRole(userId: String, roleCode: String): Boolean =
        userRoleRepository.existsByUserIdAndRoleCode(userId, roleCode)

    fun getUserPermissions(userId: String): List<Map<String, Any?>> {
        val userRoles = userRoleRepository.findByUserId(userId)
        val roleIds = userRoles.map { it.roleId }
        if (roleIds.isEmpty()) return emptyList()
        val permissions = permissionRepository.findPermissionsByRoleIds(roleIds)
        return permissions.distinctBy { it.id }.map { p ->
            mapOf(
                "id" to p.id, "name" to p.name, "action" to p.action,
                "resource" to p.resource, "type" to p.type, "path" to p.path,
            )
        }
    }

    fun hasPermission(userId: String, action: String, resource: String): Boolean {
        val userRoles = userRoleRepository.findByUserId(userId)
        val roleIds = userRoles.map { it.roleId }
        if (roleIds.isEmpty()) return false
        return permissionRepository.existsPermissionByRoleIdsAndActionAndResource(roleIds, action, resource)
    }

    fun isSuperAdmin(userId: String): Boolean = hasRole(userId, "SUPER_ADMIN")

    fun isAdmin(userId: String): Boolean {
        if (isSuperAdmin(userId)) return true
        // code 以 ADMIN 开头的角色视为管理员
        return userRoleRepository.existsByUserIdAndRoleCodePrefix(userId, "ADMIN")
    }

    // ============ 管理操作 ============

    @Transactional
    fun createUser(dto: CreateUserDto): UserResponseDto {
        if (userRepository.existsByEmail(dto.email)) throw SystemException.emailExists()
        val user = User(
            id = IdGenerator.nextId(),
            email = dto.email,
            name = dto.name,
            password = passwordEncoder.encode(dto.password)!!,
            emailVerified = true,
            preferences = defaultPreferences(),
            isActive = dto.isActive,
            loginCount = 0,
        )
        val saved = userRepository.save(user)
        dto.roleIds?.takeIf { it.isNotEmpty() }?.let { roleIds ->
            roleIds.distinct().forEach { roleId ->
                if (roleRepository.existsById(roleId) && !userRoleRepository.existsByUserIdAndRoleId(saved.id, roleId)) {
                    userRoleRepository.save(UserRole(id = IdGenerator.nextId(), userId = saved.id, roleId = roleId))
                }
            }
        }
        return UserResponseDto.from(saved)
    }

    @Transactional
    fun deleteUsers(currentUserId: String, dto: DeleteUsersDto): Int {
        val distinctIds = dto.userIds.distinct()
        val users = userRepository.findAllById(distinctIds)
        if (users.size != distinctIds.size) {
            val found = users.map { it.id }.toSet()
            val missing = distinctIds.filter { it !in found }
            throw SystemException.dataNotFound("用户不存在: ${missing.joinToString(", ")}")
        }
        if (currentUserId in distinctIds) {
            throw SystemException.operationFailed("不能删除自己")
        }
        val currentIsSuper = isSuperAdmin(currentUserId)
        users.forEach { u ->
            if (isAdmin(u.id) && !currentIsSuper) {
                throw SystemException.operationFailed("只有超级管理员可以删除其他管理员用户")
            }
        }
        // 软删除
        users.forEach { it.deletedAt = LocalDateTime.now() }
        userRepository.saveAll(users)
        // 删除关联角色
        distinctIds.forEach { userRoleRepository.deleteByUserId(it) }
        return distinctIds.size
    }

    @Transactional
    fun toggleUserStatus(currentUserId: String, dto: ToggleUserStatusDto): Int {
        val distinctIds = dto.userIds.distinct()
        val users = userRepository.findAllById(distinctIds)
        if (users.size != distinctIds.size) {
            val found = users.map { it.id }.toSet()
            val missing = distinctIds.filter { it !in found }
            throw SystemException.dataNotFound("用户不存在: ${missing.joinToString(", ")}")
        }
        if (currentUserId in distinctIds && !dto.isActive) {
            throw SystemException.operationFailed("不能禁用自己")
        }
        users.forEach { it.isActive = dto.isActive }
        userRepository.saveAll(users)
        return distinctIds.size
    }

    // ============ 私有方法 ============

    private fun validateEmailCode(email: String, code: String): Boolean {
        // get 可能返回被 JSON 解析为数字的值，用 toString() 统一处理
        val cached = cacheService.get<Any>(CacheKeys.emailVerification(email))?.toString() ?: return false
        if (cached.trim() != code.trim()) return false
        cacheService.del(CacheKeys.emailVerification(email))
        return true
    }

    private fun validateLoginCode(email: String, code: String): Boolean {
        val cached = cacheService.get<Any>(CacheKeys.loginVerification(email))?.toString() ?: return false
        if (cached != code) return false
        cacheService.del(CacheKeys.loginVerification(email))
        return true
    }

    /**
     * @description 构造默认的用户偏好设置
     * @returns UserPreferences 默认偏好（主题/语言/时区/通知/隐私均为约定值）
     *
     * @example val prefs = defaultPreferences()
     */
    private fun defaultPreferences(): UserPreferences = UserPreferences(
        theme = "light",
        language = "zh-CN",
        timezone = "Asia/Shanghai",
        notifications = UserPreferences.NotificationPrefs(email = true, push = true, sms = false),
        privacy = UserPreferences.PrivacyPrefs(profileVisible = true, showEmail = false, showLastSeen = true),
    )

    /**
     * @description 浅合并用户偏好：以已有偏好（或默认值）为基线，用 patch 覆盖同名字段
     * @param existing 现有偏好（为 null 时使用默认偏好）
     * @param patch 待覆盖的字段映射（null 值会被过滤）
     * @returns UserPreferences 合并后的偏好设置
     *
     * @example val merged = mergePreferences(user.preferences, mapOf("theme" to "dark"))
     */
    private fun mergePreferences(existing: UserPreferences?, patch: Map<String, Any?>): UserPreferences {
        val base = objectMapper.convertValue(existing ?: defaultPreferences(), Map::class.java).toMutableMap()
        base.putAll(patch.filterValues { it != null })
        return objectMapper.convertValue(base, UserPreferences::class.java)
    }
}
