package com.scx.backend.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 角色实体（对标 scx-service: prisma Role model）
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
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    val userRoles: MutableList<UserRole> = mutableListOf()

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    val rolePermissions: MutableList<RolePermission> = mutableListOf()
}
