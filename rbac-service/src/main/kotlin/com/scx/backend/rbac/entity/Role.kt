package com.scx.backend.rbac.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 角色实体
 */
@Entity
@Table(name = "roles")
class Role(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "name", length = 50, nullable = false, unique = true)
    var name: String,

    @Column(name = "code", length = 50, nullable = false, unique = true)
    var code: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "\"isSystem\"", nullable = false)
    var isSystem: Boolean = false,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    // 注：原单体中 Role 还有 @OneToMany userRoles（引用 UserRole）。
    // 微服务化后 user_roles 归 identity 服务，该跨服务关联在 rbac 中移除。
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    val rolePermissions: MutableList<RolePermission> = mutableListOf()
}
