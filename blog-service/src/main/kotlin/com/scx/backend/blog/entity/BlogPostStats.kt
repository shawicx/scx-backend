package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description 文章聚合统计实体（postId 即主键，文章创建时初始化）
 *
 * 计数经原子 UPDATE 维护（阅读/点赞）或随审核状态事务维护（评论数），
 * 不通过普通文章编辑接口直接修改。
 */
@Entity
@Table(name = "blog_post_stats")
class BlogPostStats(

    @Id
    @Column(name = "\"postId\"", length = 30, nullable = false)
    var postId: String,

    @Column(name = "\"viewCount\"", nullable = false)
    var viewCount: Long = 0,

    @Column(name = "\"likeCount\"", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "\"commentCount\"", nullable = false)
    var commentCount: Long = 0,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
