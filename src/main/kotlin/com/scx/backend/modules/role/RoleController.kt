package com.scx.backend.modules.role

import com.scx.backend.modules.role.dto.AssignPermissionsDto
import com.scx.backend.modules.role.dto.CreateRoleDto
import com.scx.backend.modules.role.dto.RoleResponseDto
import com.scx.backend.modules.role.dto.UpdateRoleDto
import com.scx.backend.modules.user.dto.MessageDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
 *
 * 路由前缀 /api/roles（由 context-path=/api 提供）
 */
@Tag(name = "角色管理", description = "角色的创建、查询、更新、删除及权限分配")
@RestController
@RequestMapping("/roles")
class RoleController(
    private val roleService: RoleService,
) {
    @Operation(summary = "创建角色", description = "新建一个角色，含名称、编码、描述及是否系统角色")
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreateRoleDto): RoleResponseDto = roleService.create(dto)

    @Operation(summary = "角色分页列表", description = "分页查询角色列表")
    @GetMapping("/list")
    fun findAll(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") limit: Int,
    ) = roleService.findAll(page, limit)

    @Operation(summary = "角色详情", description = "根据角色 ID 查询角色详情")
    @GetMapping("/detail")
    fun findById(@Parameter(description = "角色 ID") @RequestParam id: String): RoleResponseDto =
        roleService.findById(id)

    @Operation(summary = "按编码查询角色", description = "根据角色编码查询角色详情")
    @GetMapping("/by-code")
    fun findByCode(@Parameter(description = "角色编码") @RequestParam code: String): RoleResponseDto =
        roleService.findByCode(code)

    @Operation(summary = "更新角色", description = "根据角色 ID 更新角色信息")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdateRoleDto): RoleResponseDto = roleService.update(dto.id, dto)

    @Operation(summary = "删除角色", description = "根据角色 ID 删除角色")
    @DeleteMapping("/delete")
    fun delete(@Parameter(description = "角色 ID") @RequestParam id: String): MessageDto {
        roleService.delete(id)
        return MessageDto("角色删除成功")
    }

    @Operation(summary = "为角色分配权限", description = "批量给角色分配权限")
    @PostMapping("/assign-permissions")
    fun assignPermissions(@Valid @RequestBody dto: AssignPermissionsDto): MessageDto {
        roleService.assignPermissions(dto.id, dto)
        return MessageDto("权限分配成功")
    }

    @Operation(summary = "查询角色权限", description = "返回指定角色拥有的所有权限")
    @GetMapping("/permissions")
    fun getRolePermissions(@Parameter(description = "角色 ID") @RequestParam id: String) =
        roleService.getRolePermissions(id)

    @Operation(summary = "移除角色权限", description = "从角色中移除指定权限")
    @DeleteMapping("/remove-permission")
    fun removePermission(
        @Parameter(description = "角色 ID") @RequestParam id: String,
        @Parameter(description = "权限 ID") @RequestParam permissionId: String,
    ): MessageDto {
        roleService.removePermission(id, permissionId)
        return MessageDto("权限移除成功")
    }
}
