package com.scx.backend.blog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/** 每日阅读统计复合主键（文章 ID + 统计日期） */
data class BlogPostDailyStatKey(
    val postId: String = "",
    val statDate: LocalDate = LocalDate.now(),
) : Serializable

/**
 * @description 文章每日阅读统计实体（后台"今日阅读 / 近 7 天趋势"数据源）
 *
 * 阅读计数时与 blog_post_stats 同步 UPSERT；仅有累计 viewCount 无法
 * 准确计算每日阅读量，故独立按日聚合。
 */
@Entity
@Table(name = "blog_post_daily_stats")
@IdClass(BlogPostDailyStatKey::class)
class BlogPostDailyStat(

    @Id
    @Column(name = "\"postId\"", length = 30, nullable = false)
    var postId: String,

    @Id
    @Column(name = "\"statDate\"", nullable = false)
    var statDate: LocalDate,

    @Column(name = "\"viewCount\"", nullable = false)
    var viewCount: Long = 0,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
