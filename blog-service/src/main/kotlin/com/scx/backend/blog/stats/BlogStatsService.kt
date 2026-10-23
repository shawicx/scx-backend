package com.scx.backend.blog.stats

import com.scx.backend.blog.entity.BlogComment
import com.scx.backend.blog.entity.BlogPost
import com.scx.backend.blog.repository.BlogCommentRepository
import com.scx.backend.blog.repository.BlogPostDailyStatRepository
import com.scx.backend.blog.repository.BlogPostRepository
import com.scx.backend.blog.repository.BlogPostStatsRepository
import com.scx.backend.blog.stats.dto.BlogOverviewResponseDto
import com.scx.backend.blog.stats.dto.OverviewCommentDto
import com.scx.backend.blog.stats.dto.OverviewPostDto
import com.scx.backend.blog.stats.dto.TrendPointDto
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * @description 博客统计服务（管理端概览）
 *
 * 今日阅读量与趋势由每日统计表聚合（不能从累计阅读量推算）；
 * 趋势含零阅读日补零，便于前端直接绘制。
 */
@Service
class BlogStatsService(
    private val postRepository: BlogPostRepository,
    private val statsRepository: BlogPostStatsRepository,
    private val dailyStatRepository: BlogPostDailyStatRepository,
    private val commentRepository: BlogCommentRepository,
) {

    /**
     * @description 博客概览统计
     * @return BlogOverviewResponseDto 概览数据
     */
    @Transactional(readOnly = true)
    fun overview(): BlogOverviewResponseDto {
        val today = LocalDate.now()
        val since = today.minusDays(TREND_DAYS - 1L)

        val recentPosts = postRepository.findAll(
            Specification<BlogPost> { root, _, cb ->
                cb.and(
                    cb.isNull(root.get<LocalDateTime>("deletedAt")),
                    cb.isNotNull(root.get<LocalDateTime>("publishedAt")),
                )
            },
            PageRequest.of(0, RECENT_LIMIT, Sort.by(Sort.Direction.DESC, "publishedAt")),
        ).content
        val recentPostIds = recentPosts.map { it.id }
        val statsById = statsRepository.findAllById(recentPostIds).associateBy { it.postId }

        val recentComments = commentRepository.findAll(
            Specification<BlogComment> { root, _, cb ->
                val predicates = mutableListOf<Predicate>()
                predicates.add(cb.isNull(root.get<LocalDateTime>("deletedAt")))
                cb.and(*predicates.toTypedArray())
            },
            PageRequest.of(0, RECENT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")),
        ).content
        val titles = recentComments.map { it.postId }.distinct()
            .let { postRepository.findAllById(it) }
            .associate { it.id to it.title }

        return BlogOverviewResponseDto(
            totalPosts = postRepository.countByDeletedAtIsNull(),
            publishedPosts = postRepository.countByStatusAndDeletedAtIsNull(BlogPost.STATUS_PUBLISHED),
            draftPosts = postRepository.countByStatusAndDeletedAtIsNull(BlogPost.STATUS_DRAFT),
            totalViews = statsRepository.sumAllViews(),
            todayViews = dailyStatRepository.sumViewCountByDate(today),
            totalLikes = statsRepository.sumAllLikes(),
            pendingComments = commentRepository.countByStatusAndDeletedAtIsNull(BlogComment.STATUS_PENDING),
            recentPosts = recentPosts.map {
                OverviewPostDto(
                    id = it.id,
                    title = it.title,
                    status = it.status,
                    publishedAt = it.publishedAt,
                    viewCount = statsById[it.id]?.viewCount ?: 0,
                )
            },
            recentComments = recentComments.map {
                OverviewCommentDto(
                    id = it.id,
                    postTitle = titles[it.postId] ?: "",
                    nickname = it.nickname,
                    contentPreview = it.content.take(80),
                    status = it.status,
                    createdAt = it.createdAt,
                )
            },
            trend7d = buildTrend(since, today),
        )
    }

    /**
     * @description 组装近 7 天趋势（查询结果映射日期后补零）
     * @param since 起始日期（含）
     * @param today 结束日期（含）
     * @return List<TrendPointDto> 连续日期序列
     */
    private fun buildTrend(since: LocalDate, today: LocalDate): List<TrendPointDto> {
        val byDate = dailyStatRepository.findTrendSince(since).associate { it.date to it.views }
        return generateSequence(since) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { TrendPointDto(date = it, views = byDate[it] ?: 0) }
            .toList()
    }

    companion object {
        private const val TREND_DAYS = 7
        private const val RECENT_LIMIT = 5
    }
}
