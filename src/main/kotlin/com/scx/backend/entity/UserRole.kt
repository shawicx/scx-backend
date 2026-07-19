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
 * 用户-角色关联实体
 */
@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [UniqueConstraint(name = "user_roles_userId_roleId_key", columnNames = ["\"userId\"", "\"roleId\""])]
)
class UserRole(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"userId\"", columnDefinition = "char(26)", nullable = false)
    var userId: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"roleId\"", columnDefinition = "char(26)", nullable = false)
    var roleId: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"userId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var user: User? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"roleId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var role: Role? = null
}
