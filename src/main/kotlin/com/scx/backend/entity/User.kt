package com.scx.backend.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 用户实体（对标 scx-service: prisma User model）
 */
@Entity
@Table(name = "users")
class User(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "email", length = 100, nullable = false, unique = true)
    var email: String,

    @Column(name = "name", length = 50, nullable = false)
    var name: String,

    @Column(name = "password", length = 255, nullable = false)
    var password: String,

    @Column(name = "\"emailVerified\"", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "\"emailVerificationCode\"", length = 6)
    var emailVerificationCode: String? = null,

    @Column(name = "\"emailVerificationExpiry\"")
    var emailVerificationExpiry: LocalDateTime? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    var preferences: JsonNode? = null,

    @Column(name = "\"lastLoginIp\"", length = 45)
    var lastLoginIp: String? = null,

    @Column(name = "\"lastLoginAt\"")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "\"loginCount\"", nullable = false)
    var loginCount: Int = 1,

    @Column(name = "\"isActive\"", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"deletedAt\"")
    var deletedAt: LocalDateTime? = null,
) {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val userRoles: MutableList<UserRole> = mutableListOf()
}
