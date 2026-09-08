package com.scx.backend.identity.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.scx.backend.commonaudit.service.LoginLogRecorder
import com.scx.backend.identity.auth.AuthService
import com.scx.backend.identity.cache.CacheService
import com.scx.backend.identity.entity.UserRole
import com.scx.backend.identity.repository.UserRepository
import com.scx.backend.identity.repository.UserRoleRepository
import com.scx.backend.notification.mail.MailService
import com.scx.backend.rbac.entity.Permission
import com.scx.backend.rbac.repository.PermissionRepository
import com.scx.backend.rbac.repository.RoleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

/**
 * @description UserService.getMyMenus 单元测试
 *
 * 覆盖：管理员直通全量菜单、无角色用户返回空、普通用户按授权过滤并向上补全父链、按钮权限点拼接。
 */
class UserServiceMenusTest {

    private val userRepository = mock(UserRepository::class.java)
    private val roleRepository = mock(RoleRepository::class.java)
    private val userRoleRepository = mock(UserRoleRepository::class.java)
    private val permissionRepository = mock(PermissionRepository::class.java)

    private val userService = UserService(
        userRepository,
        roleRepository,
        userRoleRepository,
        permissionRepository,
        mock(CacheService::class.java),
        mock(MailService::class.java),
        mock(AuthService::class.java),
        ObjectMapper(),
        mock(LoginLogRecorder::class.java),
    )

    /** @description 构造菜单型权限实体（type=MENU，默认可见且启用） */
    private fun menu(
        id: String, name: String, path: String?, parentId: String? = null,
        level: Int = 1, sort: Int = 0, visible: Int = 1, status: Int = 1,
    ) = Permission(id = id, name = name, type = "MENU", parentId = parentId,
        level = level, path = path, sort = sort, visible = visible, status = status)

    /** @description 构造按钮型权限实体（type=BUTTON，挂在指定菜单下） */
    private fun button(id: String, name: String, action: String, resource: String, parentId: String) =
        Permission(id = id, name = name, action = action, resource = resource,
            type = "BUTTON", parentId = parentId, level = 2)

    /** @description 构造用户-角色关联实体（用户固定为 u1） */
    private fun userRole(roleId: String) = UserRole(id = "ur-$roleId", userId = "u1", roleId = roleId)

    /** @description 管理员直通全量菜单且权限点为通配 * */
    @Test
    fun `admin gets full menu tree and wildcard permission`() {
        val parent = menu("m1", "用户管理", "/users")
        val child = menu("m2", "用户列表", "/users/list", parentId = "m1", level = 2, sort = 1)
        given(permissionRepository.findMenuTreeNodes()).willReturn(listOf(parent, child))

        val result = userService.getMyMenus("u1", isAdmin = true)

        assertEquals(listOf("*"), result.permissions)
        assertEquals(1, result.menus.size)
        assertEquals("用户管理", result.menus[0].name)
        assertEquals(1, result.menus[0].children.size)
        assertEquals("用户列表", result.menus[0].children[0].name)
    }

    /** @description 无角色用户返回空菜单与空权限点 */
    @Test
    fun `user without roles gets empty result`() {
        given(userRoleRepository.findByUserId("u1")).willReturn(emptyList())

        val result = userService.getMyMenus("u1", isAdmin = false)

        assertTrue(result.menus.isEmpty())
        assertTrue(result.permissions.isEmpty())
    }

    /** @description 仅授权子菜单时向上补全未授权父菜单作为容器节点 */
    @Test
    fun `granted child menu completes invisible ancestor as container`() {
        val parent = menu("m1", "系统管理", null)
        val child = menu("m2", "用户管理", "/users", parentId = "m1", level = 2, sort = 1)
        given(permissionRepository.findMenuTreeNodes()).willReturn(listOf(parent, child))
        given(userRoleRepository.findByUserId("u1")).willReturn(listOf(userRole("r1")))
        // 角色只授权了子菜单，父菜单不在授权集合
        given(permissionRepository.findPermissionsByRoleIds(listOf("r1"))).willReturn(listOf(child))

        val result = userService.getMyMenus("u1", isAdmin = false)

        assertEquals(1, result.menus.size)
        assertEquals("系统管理", result.menus[0].name)
        assertEquals("用户管理", result.menus[0].children[0].name)
        assertTrue(result.permissions.isEmpty())
    }

    /** @description 按钮权限拼接为 resource:action 字符串 */
    @Test
    fun `buttons are flattened to resource action strings`() {
        val menuRow = menu("m1", "用户管理", "/users")
        val del = button("b1", "删除用户", action = "delete", resource = "user", parentId = "m1")
        given(permissionRepository.findMenuTreeNodes()).willReturn(listOf(menuRow))
        given(userRoleRepository.findByUserId("u1")).willReturn(listOf(userRole("r1")))
        given(permissionRepository.findPermissionsByRoleIds(listOf("r1"))).willReturn(listOf(menuRow, del))

        val result = userService.getMyMenus("u1", isAdmin = false)

        assertEquals(listOf("user:delete"), result.permissions)
        assertEquals(1, result.menus.size)
    }

    /** @description 已停用或不可见菜单对普通用户过滤剔除 */
    @Test
    fun `disabled or invisible menus are excluded for normal user`() {
        val ok = menu("m1", "用户管理", "/users")
        val disabled = menu("m2", "隐藏页", "/hidden", status = 0)
        given(permissionRepository.findMenuTreeNodes()).willReturn(listOf(ok))
        given(userRoleRepository.findByUserId("u1")).willReturn(listOf(userRole("r1")))
        // 授权集合里包含已停用的菜单（历史授权），应被过滤
        given(permissionRepository.findPermissionsByRoleIds(listOf("r1"))).willReturn(listOf(ok, disabled))

        val result = userService.getMyMenus("u1", isAdmin = false)

        assertEquals(listOf("用户管理"), result.menus.map { it.name })
    }
}
