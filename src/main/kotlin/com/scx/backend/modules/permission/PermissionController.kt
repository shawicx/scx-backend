package com.scx.backend.modules.permission

import com.scx.backend.modules.permission.dto.CreatePermissionDto
import com.scx.backend.modules.permission.dto.PermissionQueryDto
import com.scx.backend.modules.permission.dto.PermissionResponseDto
import com.scx.backend.modules.permission.dto.UpdatePermissionDto
import com.scx.backend.modules.user.dto.MessageDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 权限控制器
 *
 * 路由前缀 /api/permissions（由 context-path=/api 提供）
 */
@Tag(name = "权限管理", description = "权限（菜单/按钮）的增删改查、权限树及菜单树构建")
@RestController
@RequestMapping("/permissions")
class PermissionController(
    private val permissionService: PermissionService,
) {
    @Operation(summary = "创建权限", description = "新建一个权限节点，类型为 MENU 或 BUTTON")
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreatePermissionDto): PermissionResponseDto =
        permissionService.create(dto)

    @Operation(summary = "权限分页列表", description = "支持按动作、资源、类型、父节点、层级筛选")
    @GetMapping("/list")
    fun findAll(dto: PermissionQueryDto) = permissionService.findAll(dto)

    @Operation(summary = "权限搜索", description = "按关键字模糊搜索权限，限制返回条数")
    @GetMapping("/search")
    fun search(
        @Parameter(description = "搜索关键字") @RequestParam keyword: String,
        @Parameter(description = "最大返回条数") @RequestParam(defaultValue = "10") limit: Int,
    ) = permissionService.search(keyword, limit)

    @Operation(summary = "查询所有动作", description = "返回权限中出现的去重动作列表")
    @GetMapping("/actions")
    fun getUniqueActions(): List<String> = permissionService.getUniqueActions()

    @Operation(summary = "查询所有资源", description = "返回权限中出现的去重资源列表")
    @GetMapping("/resources")
    fun getUniqueResources(): List<String> = permissionService.getUniqueResources()

    @Operation(summary = "按动作查询权限", description = "返回指定动作对应的权限列表")
    @GetMapping("/by-action")
    fun findByAction(@Parameter(description = "动作名称") @RequestParam action: String) =
        permissionService.findByAction(action)

    @Operation(summary = "按资源查询权限", description = "返回指定资源对应的权限列表")
    @GetMapping("/by-resource")
    fun findByResource(@Parameter(description = "资源名称") @RequestParam resource: String) =
        permissionService.findByResource(resource)

    @Operation(summary = "权限详情", description = "根据权限 ID 查询权限详情")
    @GetMapping("/detail")
    fun findById(@Parameter(description = "权限 ID") @RequestParam id: String): PermissionResponseDto =
        permissionService.findById(id)

    @Operation(summary = "更新权限", description = "根据权限 ID 更新权限信息")
    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdatePermissionDto): PermissionResponseDto =
        permissionService.update(dto.id, dto)

    @Operation(summary = "权限树", description = "返回包含子节点的完整权限树结构")
    @GetMapping("/tree")
    fun getTree() = permissionService.getTree()

    @Operation(summary = "菜单树", description = "返回精简的菜单树（仅菜单字段），供前端渲染导航")
    @GetMapping("/menu-tree")
    fun getMenuTree() = permissionService.getMenuTree()

    @Operation(summary = "一级菜单", description = "返回所有一级菜单（level=1）节点")
    @GetMapping("/level-1")
    fun getFirstLevelMenus() = permissionService.getFirstLevelMenus()

    @Operation(summary = "按层级查询", description = "返回指定层级的所有权限节点")
    @GetMapping("/by-level")
    fun getByLevel(@Parameter(description = "层级") @RequestParam level: Int) =
        permissionService.getByLevel(level)

    @Operation(summary = "菜单下的按钮", description = "返回指定菜单节点下的所有按钮权限")
    @GetMapping("/{menuId}/buttons")
    fun getButtonsByMenuId(@Parameter(description = "菜单 ID") @PathVariable menuId: String) =
        permissionService.getButtonsByMenuId(menuId)

    @Operation(summary = "删除权限", description = "级联删除权限节点及其子节点")
    @DeleteMapping("/delete")
    fun delete(@Parameter(description = "权限 ID") @RequestParam id: String): MessageDto {
        permissionService.deleteCascade(id)
        return MessageDto("权限删除成功")
    }
}
