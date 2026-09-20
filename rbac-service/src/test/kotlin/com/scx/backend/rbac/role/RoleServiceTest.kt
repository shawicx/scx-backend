package com.scx.backend.rbac.role

import com.scx.backend.common.exception.SystemErrorCode
import com.scx.backend.common.exception.SystemException
import com.scx.backend.rbac.entity.Role
import com.scx.backend.rbac.permission.PermissionService
import com.scx.backend.rbac.repository.PermissionRepository
import com.scx.backend.rbac.repository.RoleRepository
import com.scx.backend.rbac.role.dto.CreateRoleDto
import com.scx.backend.rbac.role.dto.UpdateRoleDto
import com.scx.backend.rbac.rolepermission.RolePermissionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.Optional

/**
 * @description 角色数据范围配置单元测试
 *
 * 纯 Mockito 单元测试：覆盖 dataScope 的可配置档位（ALL/SELF）、
 * 缺省 SELF、部门级预留档位拒绝（部门体系未上线）、更新改档。
 */
class RoleServiceTest {

    private val roleRepository = mock(RoleRepository::class.java)
    private val permissionRepository = mock(PermissionRepository::class.java)
    private val rolePermissionService = mock(RolePermissionService::class.java)
    private val permissionService = mock(PermissionService::class.java)
    private val roleService = RoleService(roleRepository, permissionRepository, rolePermissionService, permissionService)

    @Test
    fun `create persists configurable data scope`() {
        given(roleRepository.findByName("编辑")).willReturn(null)
        given(roleRepository.findByCode("EDITOR")).willReturn(null)
        given(roleRepository.save(any<Role>())).willAnswer { it.arguments[0] }

        val dto = roleService.create(CreateRoleDto(name = "编辑", code = "EDITOR", dataScope = "ALL"))

        assertEquals("ALL", dto.dataScope, "创建时应落库并返回 dataScope")
    }

    @Test
    fun `create defaults data scope to SELF`() {
        given(roleRepository.findByName("访客")).willReturn(null)
        given(roleRepository.findByCode("GUEST")).willReturn(null)
        given(roleRepository.save(any<Role>())).willAnswer { it.arguments[0] }

        val dto = roleService.create(CreateRoleDto(name = "访客", code = "GUEST"))

        assertEquals("SELF", dto.dataScope, "未配置 dataScope 时应缺省 SELF")
    }

    @Test
    fun `create rejects reserved department scopes`() {
        val ex = assertThrows<SystemException> {
            roleService.create(CreateRoleDto(name = "部门编辑", code = "DEPT_EDITOR", dataScope = "DEPT"))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code, "部门级档位未上线应拒绝配置")
    }

    @Test
    fun `update changes data scope`() {
        val role = roleEntity()
        given(roleRepository.findById("r-1")).willReturn(Optional.of(role))
        given(roleRepository.save(any<Role>())).willAnswer { it.arguments[0] }

        val dto = roleService.update("r-1", UpdateRoleDto(id = "r-1", dataScope = "ALL"))

        assertEquals("ALL", dto.dataScope)
        assertEquals("ALL", role.dataScope)
    }

    /**
     * @description 构造测试用非系统角色实体
     * @returns Role 角色实体
     */
    private fun roleEntity(): Role = Role(
        id = "r-1",
        name = "编辑",
        code = "EDITOR",
        description = null,
    )
}
