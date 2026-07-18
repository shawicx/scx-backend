package com.scx.backend.modules.permission

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.entity.Permission
import com.scx.backend.modules.permission.dto.CreatePermissionDto
import com.scx.backend.modules.permission.dto.PermissionMenuTreeDto
import com.scx.backend.modules.permission.dto.PermissionQueryDto
import com.scx.backend.modules.permission.dto.PermissionResponseDto
import com.scx.backend.modules.permission.dto.PermissionTreeResponseDto
import com.scx.backend.modules.permission.dto.UpdatePermissionDto
import com.scx.backend.repository.PermissionRepository
import com.scx.backend.repository.RolePermissionRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 权限服务
 * 对标 scx-service: src/modules/permission/permission.service.ts
 *
 * 树形权限：MENU(菜单) / BUTTON(按钮)，自引用 parentId，level 自动计算。
 */
@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
) {
    private val logger = LoggerFactory.getLogger(PermissionService::class.java)

    @Transactional
    fun create(dto: CreatePermissionDto): PermissionResponseDto {
        val level = calculateLevel(dto.parentId, dto.type)
        val permission = Permission(
            id = IdGenerator.nextId(),
            name = dto.name,
            action = dto.action,
            resource = dto.resource,
            description = dto.description,
            type = dto.type,
            parentId = dto.parentId,
            level = level,
            path = dto.path,
            icon = dto.icon,
            sort = dto.sort ?: 0,
            visible = dto.visible ?: 1,
            status = dto.status ?: 1,
        )
        val saved = permissionRepository.save(permission)
        logger.info("Permission created: {} ({})", saved.name, saved.type)
        return PermissionResponseDto.from(saved)
    }

    /**
     * 根据父节点和类型计算 level
     * - 无父 + MENU → 1
     * - 无父 + BUTTON → 报错（按钮必须有父）
     * - 一级菜单父 + MENU → 2
     * - 一/二级菜单父 + BUTTON → parent.level + 1
     */
    fun calculateLevel(parentId: String?, type: String): Int {
        if (parentId.isNullOrBlank()) {
            if (type == "BUTTON") {
                throw SystemException.invalidParameter("按钮必须有父节点")
            }
            return 1
        }
        val parent = permissionRepository.findById(parentId).orElseThrow {
            SystemException.dataNotFound("父权限不存在")
        }
        return when (type) {
            "BUTTON" -> {
                if (parent.level != 1 && parent.level != 2) {
                    throw SystemException.invalidParameter("按钮必须挂在一级或二级菜单下")
                }
                parent.level + 1
            }
            "MENU" -> {
                if (parent.level != 1) {
                    throw SystemException.invalidParameter("二级菜单必须挂在一级菜单下")
                }
                2
            }
            else -> throw SystemException.invalidParameter("权限类型必须是MENU或BUTTON")
        }
    }

    fun findAll(dto: PermissionQueryDto): Map<String, Any> {
        val sort = Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageable = PageRequest.of(dto.page - 1, dto.limit, sort)
        val spec = Specification<Permission> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            dto.search?.takeIf { it.isNotBlank() }?.let { kw ->
                val pattern = "%${kw.lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("resource")), pattern),
                    ),
                )
            }
            dto.action?.takeIf { it.isNotBlank() }?.let { predicates.add(cb.equal(root.get<String>("action"), it)) }
            dto.resource?.takeIf { it.isNotBlank() }?.let { predicates.add(cb.equal(root.get<String>("resource"), it)) }
            dto.type?.let { predicates.add(cb.equal(root.get<String>("type"), it)) }
            dto.parentId?.takeIf { it.isNotBlank() }?.let { predicates.add(cb.equal(root.get<String>("parentId"), it)) }
            dto.level?.let { predicates.add(cb.equal(root.get<Int>("level"), it)) }
            cb.and(*predicates.toTypedArray())
        }
        val page = permissionRepository.findAll(spec, pageable)
        return mapOf("list" to page.content.map { PermissionResponseDto.from(it) }, "total" to page.totalElements)
    }

    fun findById(id: String): PermissionResponseDto {
        val p = permissionRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Permission with ID '$id' not found")
        }
        return PermissionResponseDto.from(p)
    }

    fun findByAction(action: String): List<PermissionResponseDto> =
        permissionRepository.findByAction(action).map { PermissionResponseDto.from(it) }

    fun findByResource(resource: String): List<PermissionResponseDto> =
        permissionRepository.findByResource(resource).map { PermissionResponseDto.from(it) }

    @Transactional
    fun update(id: String, dto: UpdatePermissionDto): PermissionResponseDto {
        val permission = permissionRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Permission with ID '$id' not found")
        }
        dto.name?.takeIf { it != permission.name }?.let { newName ->
            permissionRepository.findByName(newName)?.let { existing ->
                if (existing.id != id) throw SystemException.resourceExists("Permission with name '$newName' already exists")
            }
        }
        dto.name?.let { permission.name = it }
        dto.action?.let { permission.action = it }
        dto.resource?.let { permission.resource = it }
        dto.description?.let { permission.description = it }
        dto.path?.let { permission.path = it }
        dto.icon?.let { permission.icon = it }
        dto.sort?.let { permission.sort = it }
        dto.visible?.let { permission.visible = it }
        dto.status?.let { permission.status = it }
        // 父节点或类型变更时重新计算 level
        val newParentId = dto.parentId ?: permission.parentId
        val newType = dto.type ?: permission.type
        if ((dto.parentId != null && dto.parentId != permission.parentId) ||
            (dto.type != null && dto.type != permission.type)
        ) {
            permission.level = calculateLevel(newParentId, newType)
            permission.parentId = newParentId
            permission.type = newType
        }
        val saved = permissionRepository.save(permission)
        logger.info("Permission updated: {}", saved.name)
        return PermissionResponseDto.from(saved)
    }

    /**
     * 删除（带角色引用检查）。子节点由 DB 外键 ON DELETE CASCADE 自动级联删除。
     */
    @Transactional
    fun delete(id: String) {
        val permission = permissionRepository.findById(id).orElseThrow {
            SystemException.dataNotFound("Permission with ID '$id' not found")
        }
        val roleCount = rolePermissionRepository.countByPermissionId(id)
        if (roleCount > 0) {
            throw SystemException.resourceExists(
                "Cannot delete permission '${permission.name}' as it is assigned to $roleCount role(s)",
            )
        }
        permissionRepository.delete(permission)
        logger.info("Permission deleted: {} ({}:{})", permission.name, permission.action, permission.resource)
    }

    /** 级联删除（与 delete 相同，DB 自动级联子节点） */
    @Transactional
    fun deleteCascade(id: String) = delete(id)

    fun getUniqueActions(): List<String> =
        permissionRepository.findDistinctActions().sorted()

    fun getUniqueResources(): List<String> =
        permissionRepository.findDistinctResources().sorted()

    fun search(keyword: String, limit: Int = 10): List<PermissionResponseDto> =
        permissionRepository.searchByKeyword(keyword.lowercase(), PageRequest.of(0, limit))
            .map { PermissionResponseDto.from(it) }

    /** 完整权限树（含按钮） */
    fun getTree(): List<PermissionTreeResponseDto> {
        val all = permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.DESC, "createdAt")))
        return buildTree(all, null)
    }

    /** 菜单树（仅可见启用的 MENU） */
    fun getMenuTree(): List<PermissionMenuTreeDto> {
        val menus = permissionRepository.findMenuTreeNodes()
        return buildMenuTree(menus, null)
    }

    fun getButtonsByMenuId(menuId: String): List<PermissionResponseDto> =
        permissionRepository.findButtonsByMenuId(menuId).map { PermissionResponseDto.from(it) }

    fun getByLevel(level: Int): List<PermissionResponseDto> =
        permissionRepository.findByLevelAndStatus(level, 1).map { PermissionResponseDto.from(it) }

    fun getFirstLevelMenus(): List<PermissionResponseDto> = getByLevel(1)

    // ============ 树构建 ============

    private fun buildTree(all: List<Permission>, parentId: String?): List<PermissionTreeResponseDto> =
        all.filter { it.parentId == parentId }
            .map { perm ->
                PermissionResponseDto.from(perm).let { dto ->
                    PermissionTreeResponseDto(
                        id = dto.id, name = dto.name, type = dto.type, action = dto.action,
                        resource = dto.resource, parentId = dto.parentId, level = dto.level,
                        path = dto.path, icon = dto.icon, sort = dto.sort, visible = dto.visible,
                        status = dto.status, description = dto.description,
                        createdAt = dto.createdAt, updatedAt = dto.updatedAt,
                        children = buildTree(all, perm.id),
                    )
                }
            }

    private fun buildMenuTree(all: List<Permission>, parentId: String?): List<PermissionMenuTreeDto> =
        all.filter { it.parentId == parentId }
            .map { perm ->
                PermissionMenuTreeDto(
                    id = perm.id, name = perm.name, path = perm.path, icon = perm.icon,
                    children = buildMenuTree(all, perm.id),
                )
            }
}
