package com.scx.backend.blog.stats.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * @description 博客统计相关 DTO（管理端概览）
 */

/** 近 N 天阅读趋势行 */
@Schema(description = "每日阅读趋势行")
data class TrendPointDto(
    @Schema(description = "日期（yyyy-MM-dd）") val date: LocalDate,
    @Schema(description = "当日阅读数") val views: Long,
)

/** 概览最近文章行 */
@Schema(description = "概览最近文章行")
data class OverviewPostDto(
    @Schema(description = "文章 ID") val id: String,
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "状态（0 草稿 / 1 已发布 / 2 已归档）") val status: Int,
    @Schema(description = "发布时间") val publishedAt: LocalDateTime?,
    @Schema(description = "累计阅读数") val viewCount: Long,
)

/** 概览最新评论行 */
@Schema(description = "概览最新评论行")
data class OverviewCommentDto(
    @Schema(description = "评论 ID") val id: String,
    @Schema(description = "所属文章标题") val postTitle: String,
    @Schema(description = "访客昵称") val nickname: String,
    @Schema(description = "评论内容摘要") val contentPreview: String,
    @Schema(description = "状态（0 待审核 / 1 已通过 / 2 已拒绝）") val status: Int,
    @Schema(description = "提交时间") val createdAt: LocalDateTime,
)

/** 博客概览统计响应 */
@Schema(description = "博客概览统计响应")
data class BlogOverviewResponseDto(
    @Schema(description = "未删除文章总数") val totalPosts: Long,
    @Schema(description = "已发布文章数") val publishedPosts: Long,
    @Schema(description = "草稿文章数") val draftPosts: Long,
    @Schema(description = "累计阅读量") val totalViews: Long,
    @Schema(description = "今日阅读量") val todayViews: Long,
    @Schema(description = "累计有效点赞数") val totalLikes: Long,
    @Schema(description = "待审核评论数") val pendingComments: Long,
    @Schema(description = "最近发布文章（最多 5 条）") val recentPosts: List<OverviewPostDto>,
    @Schema(description = "最新评论（最多 5 条）") val recentComments: List<OverviewCommentDto>,
    @Schema(description = "近 7 天阅读趋势（含零阅读日）") val trend7d: List<TrendPointDto>,
)
