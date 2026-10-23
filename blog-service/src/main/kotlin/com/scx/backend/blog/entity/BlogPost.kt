package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 博客文章实体（Markdown 原文存储，HTML 渲染由博客前台负责）
 *
 * status：0 草稿 / 1 已发布 / 2 已归档；visibility：0 公开 / 1 私密。
 * 公开可见 = status=1 且 visibility=0 且 deletedAt 为空 且 publishedAt 已到。
 * "coverFileId"/"authorId" 为跨服务逻辑引用（files/users 表），不建 JPA 关联。
 */
@Entity
@Table(name = "blog_posts")
class BlogPost(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "title", length = 200, nullable = false)
    var title: String,

    @Column(name = "slug", length = 220, nullable = false)
    var slug: String,

    @Column(name = "summary", length = 500)
    var summary: String? = null,

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String,

    @Column(name = "\"coverFileId\"", length = 30)
    var coverFileId: String? = null,

    @Column(name = "\"categoryId\"", length = 30)
    var categoryId: String? = null,

    @Column(name = "\"authorId\"", length = 30)
    var authorId: String? = null,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", nullable = false, columnDefinition = "smallint")
    var status: Int = STATUS_DRAFT,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "visibility", nullable = false, columnDefinition = "smallint")
    var visibility: Int = VISIBILITY_PUBLIC,

    @Column(name = "\"isTop\"", nullable = false)
    var isTop: Boolean = false,

    @Column(name = "\"allowComment\"", nullable = false)
    var allowComment: Boolean = true,

    @Column(name = "\"publishedAt\"")
    var publishedAt: LocalDateTime? = null,

    @Column(name = "\"seoTitle\"", length = 200)
    var seoTitle: String? = null,

    @Column(name = "\"seoDescription\"", length = 500)
    var seoDescription: String? = null,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"deletedAt\"")
    var deletedAt: LocalDateTime? = null,
) {
    companion object {
        const val STATUS_DRAFT = 0
        const val STATUS_PUBLISHED = 1
        const val STATUS_ARCHIVED = 2

        const val VISIBILITY_PUBLIC = 0
        const val VISIBILITY_PRIVATE = 1
    }
}
