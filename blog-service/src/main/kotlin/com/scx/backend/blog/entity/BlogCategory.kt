package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 博客分类实体（单层，一篇文章最多一个分类）
 *
 * status：0 停用 / 1 启用；停用后公开接口不再展示，但不改变文章自身公开策略。
 */
@Entity
@Table(name = "blog_categories")
class BlogCategory(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "name", length = 80, nullable = false)
    var name: String,

    @Column(name = "slug", length = 100, nullable = false)
    var slug: String,

    @Column(name = "description", length = 500)
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
