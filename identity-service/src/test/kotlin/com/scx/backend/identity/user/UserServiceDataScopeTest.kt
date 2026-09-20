package com.scx.backend.identity.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.common.security.DataScope
import com.scx.backend.commonaudit.service.LoginLogRecorder
import com.scx.backend.identity.auth.AuthService
import com.scx.backend.identity.cache.CacheService
import com.scx.backend.identity.repository.UserRepository
import com.scx.backend.identity.repository.UserRoleRepository
import com.scx.backend.notification.mail.MailService
import com.scx.backend.rbac.repository.PermissionRepository
import com.scx.backend.rbac.repository.RoleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

/**
 * @description 用户数据范围解析单元测试
 *
 * 纯 Mockito 单元测试：覆盖 resolveDataScope 的合并规则——
 * SUPER_ADMIN 隐含 ALL、多角色取最宽、无角色默认 SELF。
 */
class UserServiceDataScopeTest {

    private val userRepository = mock(UserRepository::class.java)
    private val roleRepository = mock(RoleRepository::class.java)
    private val userRoleRepository = mock(UserRoleRepository::class.java)
    private val permissionRepository = mock(PermissionRepository::class.java)
    private val cacheService = mock(CacheService::class.java)
    private val mailService = mock(MailService::class.java)
    private val authService = mock(AuthService::class.java)
    private val objectMapper = mock(ObjectMapper::class.java)
    private val loginLogRecorder = mock(LoginLogRecorder::class.java)
    private val userService = UserService(
        userRepository,
        roleRepository,
        userRoleRepository,
        permissionRepository,
        cacheService,
        mailService,
        authService,
        objectMapper,
        loginLogRecorder,
    )

    @Test
    fun `resolveDataScope returns ALL for super admin`() {
        given(userRoleRepository.existsByUserIdAndRoleCode("u1", "SUPER_ADMIN")).willReturn(true)

        assertEquals(DataScope.ALL, userService.resolveDataScope("u1"))
    }

    @Test
    fun `resolveDataScope merges widest across roles`() {
        given(userRoleRepository.existsByUserIdAndRoleCode(anyString(), anyString())).willReturn(false)
        given(userRoleRepository.findDataScopesByUserId("u1")).willReturn(listOf("SELF", "ALL"))

        assertEquals(DataScope.ALL, userService.resolveDataScope("u1"))
    }

    @Test
    fun `resolveDataScope stays SELF when only self scoped roles`() {
        given(userRoleRepository.existsByUserIdAndRoleCode(anyString(), anyString())).willReturn(false)
        given(userRoleRepository.findDataScopesByUserId("u1")).willReturn(listOf("SELF"))

        assertEquals(DataScope.SELF, userService.resolveDataScope("u1"))
    }

    @Test
    fun `resolveDataScope defaults to SELF without roles`() {
        given(userRoleRepository.existsByUserIdAndRoleCode(anyString(), anyString())).willReturn(false)
        given(userRoleRepository.findDataScopesByUserId("u1")).willReturn(emptyList())

        assertEquals(DataScope.SELF, userService.resolveDataScope("u1"))
    }

    /**
     * @description 任意字符串匹配器（Kotlin 非空参数需空安全兜底，避免 any() 返回 null 触发空检查）
     * @returns String 匹配占位值
     */
    private fun anyString(): String = any<String>() ?: ""
}
