package com.scx.backend.modules.role

import com.scx.backend.modules.role.dto.AssignPermissionsDto
import com.scx.backend.modules.role.dto.CreateRoleDto
import com.scx.backend.modules.role.dto.RoleResponseDto
import com.scx.backend.modules.role.dto.UpdateRoleDto
import com.scx.backend.modules.user.dto.MessageDto
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 角色控制器
 * 对标 scx-service: src/modules/role/role.controller.ts
 *
 * 路由前缀 /api/roles（由 context-path=/api 提供）
 */
@RestController
@RequestMapping("/roles")
class RoleController(
    private val roleService: RoleService,
) {
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreateRoleDto): RoleResponseDto = roleService.create(dto)

    @GetMapping("/list")
    fun findAll(@RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "10") limit: Int) =
        roleService.findAll(page, limit)

    @GetMapping("/detail")
    fun findById(@RequestParam id: String): RoleResponseDto = roleService.findById(id)

    @GetMapping("/by-code")
    fun findByCode(@RequestParam code: String): RoleResponseDto = roleService.findByCode(code)

    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateRoleDto): RoleResponseDto = roleService.update(dto.id, dto)

    @DeleteMapping("/delete")
    fun delete(@RequestParam id: String): MessageDto {
        roleService.delete(id)
        return MessageDto("角色删除成功")
    }

    @PostMapping("/assign-permissions")
    fun assignPermissions(@Valid @RequestBody dto: AssignPermissionsDto): MessageDto {
        roleService.assignPermissions(dto.id, dto)
        return MessageDto("权限分配成功")
    }

    @GetMapping("/permissions")
    fun getRolePermissions(@RequestParam id: String) = roleService.getRolePermissions(id)

    @DeleteMapping("/remove-permission")
    fun removePermission(@RequestParam id: String, @RequestParam permissionId: String): MessageDto {
        roleService.removePermission(id, permissionId)
        return MessageDto("权限移除成功")
    }
}
