package com.scx.backend.rbac.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 字典类型实体
 */
@Entity
@Table(name = "dict_type")
class DictType(
    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "name", length = 100, nullable = false, unique = true)
    var name: String,

    @Column(name = "code", length = 100, nullable = false, unique = true)
    var code: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "\"isSystem\"", nullable = false)
    var isSystem: Boolean = false,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", columnDefinition = "smallint", nullable = false)
    var status: Int = 1,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
