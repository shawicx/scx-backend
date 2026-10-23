package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description 匿名点赞记录实体（服务端派生访客标识去重）
 *
 * 唯一约束 (postId, visitorKeyHash) 防止同一访客重复点赞；
 * 点赞/取消点赞均幂等，计数与记录在事务内保持一致。
 */
@Entity
@Table(name = "blog_post_likes")
class BlogPostLike(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "\"postId\"", length = 30, nullable = false)
    var postId: String,

    @Column(name = "\"visitorKeyHash\"", length = 128, nullable = false)
    var visitorKeyHash: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
