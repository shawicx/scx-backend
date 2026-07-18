package com.scx.backend.modules.permission

import com.scx.backend.modules.permission.dto.CreatePermissionDto
import com.scx.backend.modules.permission.dto.PermissionQueryDto
import com.scx.backend.modules.permission.dto.PermissionResponseDto
import com.scx.backend.modules.permission.dto.UpdatePermissionDto
import com.scx.backend.modules.user.dto.MessageDto
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
 * 对标 scx-service: src/modules/permission/permission.controller.ts
 *
 * 路由前缀 /api/permissions（由 context-path=/api 提供）
 */
@RestController
@RequestMapping("/permissions")
class PermissionController(
    private val permissionService: PermissionService,
) {
    @PostMapping("/create")
    fun create(@Valid @RequestBody dto: CreatePermissionDto): PermissionResponseDto =
        permissionService.create(dto)

    @GetMapping("/list")
    fun findAll(dto: PermissionQueryDto) = permissionService.findAll(dto)

    @GetMapping("/search")
    fun search(@RequestParam keyword: String, @RequestParam(defaultValue = "10") limit: Int) =
        permissionService.search(keyword, limit)

    @GetMapping("/actions")
    fun getUniqueActions(): List<String> = permissionService.getUniqueActions()

    @GetMapping("/resources")
    fun getUniqueResources(): List<String> = permissionService.getUniqueResources()

    @GetMapping("/by-action")
    fun findByAction(@RequestParam action: String) = permissionService.findByAction(action)

    @GetMapping("/by-resource")
    fun findByResource(@RequestParam resource: String) = permissionService.findByResource(resource)

    @GetMapping("/detail")
    fun findById(@RequestParam id: String): PermissionResponseDto = permissionService.findById(id)

    @PutMapping("/update")
    fun update(@Valid @RequestBody dto: UpdatePermissionDto): PermissionResponseDto =
        permissionService.update(dto.id, dto)

    @GetMapping("/tree")
    fun getTree() = permissionService.getTree()

    @GetMapping("/menu-tree")
    fun getMenuTree() = permissionService.getMenuTree()

    @GetMapping("/level-1")
    fun getFirstLevelMenus() = permissionService.getFirstLevelMenus()

    @GetMapping("/by-level")
    fun getByLevel(@RequestParam level: Int) = permissionService.getByLevel(level)

    @GetMapping("/{menuId}/buttons")
    fun getButtonsByMenuId(@PathVariable menuId: String) = permissionService.getButtonsByMenuId(menuId)

    @DeleteMapping("/delete")
    fun delete(@RequestParam id: String): MessageDto {
        permissionService.deleteCascade(id)
        return MessageDto("权限删除成功")
    }
}
