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
import java.time.LocalDateTime

/**
 * 文件实体
 *
 * 注意：上传与存储业务逻辑暂未实现，本实体仅保证 schema 完整。
 */
@Entity
@Table(name = "files")
class File(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"userId\"", columnDefinition = "char(26)", nullable = false)
    var userId: String,

    @Column(name = "\"originalName\"", length = 255, nullable = false)
    var originalName: String,

    @Column(name = "\"mimeType\"", length = 100, nullable = false)
    var mimeType: String,

    @Column(name = "size", nullable = false)
    var size: Int,

    @Column(name = "path", length = 500, nullable = false)
    var path: String,

    @Column(name = "url", length = 500, nullable = false)
    var url: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"deletedAt\"")
    var deletedAt: LocalDateTime? = null,
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"userId\"", referencedColumnName = "id", insertable = false, updatable = false)
    var user: User? = null
}
