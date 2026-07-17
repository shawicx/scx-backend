package com.scx.backend.entity

import jakarta.persistence.Column
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 权限实体（对标 scx-service: prisma Permission model）
 * 树形结构：MENU(菜单) / BUTTON(按钮)，通过 parentId 自引用
 */
@Entity
@Table(name = "permissions")
class Permission(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "name", length = 100, nullable = false, unique = true)
    var name: String,

    @Column(name = "action", length = 50)
    var action: String? = null,

    @Column(name = "resource", length = 100)
    var resource: String? = null,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "type", length = 20, nullable = false)
    var type: String = "BUTTON",

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"parentId\"", columnDefinition = "char(26)")
    var parentId: String? = null,

    @Column(name = "level", nullable = false)
    var level: Int = 0,

    @Column(name = "path", length = 200)
    var path: String? = null,

    @Column(name = "icon", length = 100)
    var icon: String? = null,

    @Column(name = "sort", nullable = false)
    var sort: Int = 0,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "visible", columnDefinition = "smallint", nullable = false)
    var visible: Int = 1,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", columnDefinition = "smallint", nullable = false)
    var status: Int = 1,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"parentId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var parent: Permission? = null

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    val children: MutableList<Permission> = mutableListOf()

    @OneToMany(mappedBy = "permission", fetch = FetchType.LAZY)
    val rolePermissions: MutableList<RolePermission> = mutableListOf()
}
