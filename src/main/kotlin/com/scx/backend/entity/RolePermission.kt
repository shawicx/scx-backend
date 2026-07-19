package com.scx.backend.entity

import jakarta.persistence.Column
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 角色-权限关联实体
 */
@Entity
@Table(
    name = "role_permissions",
    uniqueConstraints = [UniqueConstraint(name = "role_permissions_roleId_permissionId_key", columnNames = ["\"roleId\"", "\"permissionId\""])]
)
class RolePermission(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"roleId\"", columnDefinition = "char(26)", nullable = false)
    var roleId: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"permissionId\"", columnDefinition = "char(26)", nullable = false)
    var permissionId: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"roleId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var role: Role? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"permissionId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var permission: Permission? = null
}
