package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime

/** 文章标签关联复合主键（文章 ID + 标签 ID） */
data class BlogPostTagKey(
    val postId: String = "",
    val tagId: String = "",
) : Serializable

/**
 * @description 文章与标签关联实体（多对多，复合主键）
 *
 * 修改文章标签时由服务层在事务内先删后插整体替换。
 */
@Entity
@Table(name = "blog_post_tags")
@IdClass(BlogPostTagKey::class)
class BlogPostTag(

    @Id
    @Column(name = "\"postId\"", length = 30, nullable = false)
    var postId: String,

    @Id
    @Column(name = "\"tagId\"", length = 30, nullable = false)
    var tagId: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
