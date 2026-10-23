package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 博客标签实体（与文章多对多，关联见 BlogPostTag）
 *
 * status：0 停用 / 1 启用；停用后不允许新文章选择，历史关联保留。
 */
@Entity
@Table(name = "blog_tags")
class BlogTag(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "name", length = 60, nullable = false)
    var name: String,

    @Column(name = "slug", length = 80, nullable = false)
    var slug: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "sort", nullable = false)
    var sort: Int = 0,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", nullable = false, columnDefinition = "smallint")
    var status: Int = 1,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
