package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 博客评论实体（匿名，先审后显；一级评论 + 对一级评论的回复）
 *
 * status：0 待审核 / 1 已通过 / 2 已拒绝；公开接口只返回 status=1 且未删除。
 * "visitorKeyHash"/"ipHash" 为服务端派生的脱敏标识，永不进入公开响应；
 * "parentId" 指向直接回复的评论，"rootId" 指向所属根评论（一级评论两者均为空）。
 */
@Entity
@Table(name = "blog_comments")
class BlogComment(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "\"postId\"", length = 30, nullable = false)
    var postId: String,

    @Column(name = "\"parentId\"", length = 30)
    var parentId: String? = null,

    @Column(name = "\"rootId\"", length = 30)
    var rootId: String? = null,

    @Column(name = "nickname", length = 60, nullable = false)
    var nickname: String,

    @Column(name = "email", length = 254)
    var email: String? = null,

    @Column(name = "website", length = 255)
    var website: String? = null,

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "status", nullable = false, columnDefinition = "smallint")
    var status: Int = STATUS_PENDING,

    @Column(name = "\"visitorKeyHash\"", length = 128)
    var visitorKeyHash: String? = null,

    @Column(name = "\"ipHash\"", length = 128)
    var ipHash: String? = null,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"deletedAt\"")
    var deletedAt: LocalDateTime? = null,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_APPROVED = 1
        const val STATUS_REJECTED = 2
    }
}
