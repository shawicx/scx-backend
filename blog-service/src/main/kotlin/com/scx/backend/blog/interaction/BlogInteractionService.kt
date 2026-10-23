package com.scx.backend.blog.interaction

import com.scx.backend.blog.entity.BlogPostDailyStat
import com.scx.backend.blog.entity.BlogPostLike
import com.scx.backend.blog.interaction.dto.LikeStatusDto
import com.scx.backend.blog.post.BlogPostService
import com.scx.backend.blog.repository.BlogPostDailyStatRepository
import com.scx.backend.blog.repository.BlogPostLikeRepository
import com.scx.backend.blog.repository.BlogPostStatsRepository
import com.scx.backend.blog.support.BlogRateLimiter
import com.scx.backend.blog.support.VisitorIdentity
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * @description 博客互动服务（匿名阅读统计 / 点赞与取消点赞）
 *
 * 阅读计数经数据库原子 UPDATE + 每日统计 UPSERT 维护；
 * 点赞以服务端派生的访客标识去重（唯一约束兜底），点赞/取消均幂等，
 * 计数与记录在事务内保持一致；两者均有基础频率限制。
 */
@Service
class BlogInteractionService(
    private val postService: BlogPostService,
    private val statsRepository: BlogPostStatsRepository,
    private val likeRepository: BlogPostLikeRepository,
    private val dailyStatRepository: BlogPostDailyStatRepository,
    private val visitorIdentity: VisitorIdentity,
    private val rateLimiter: BlogRateLimiter,
) {

    /**
     * @description 上报一次阅读（文章须公开可见；按 IP 限流）
     * @param slug 文章 slug
     * @param request HTTP 请求（派生访客标识）
     */
    @Transactional
    fun recordView(slug: String, request: HttpServletRequest) {
        val post = postService.requirePublicPost(slug)
        val ipHash = visitorIdentity.ipHash(request)
        if (!rateLimiter.tryAcquire("view:$ipHash", limit = VIEW_RATE_LIMIT, windowMillis = RATE_WINDOW_MS)) {
            // 阅读上报超限直接忽略，不报错干扰访客
            return
        }
        statsRepository.incrementView(post.id)
        val today = LocalDate.now()
        if (dailyStatRepository.incrementView(post.id, today) == 0) {
            // 当日行不存在：并发下唯一约束兜底，重复插入静默失败即可
            runCatching {
                dailyStatRepository.save(BlogPostDailyStat(postId = post.id, statDate = today, viewCount = 1))
            }
        }
    }

    /**
     * @description 点赞（幂等：已点赞直接返回当前状态）
     * @param slug 文章 slug
     * @param request HTTP 请求（派生访客标识）
     * @return LikeStatusDto 点赞数与当前访客点赞状态
     */
    @Transactional
    fun like(slug: String, request: HttpServletRequest): LikeStatusDto {
        val post = postService.requirePublicPost(slug)
        val ipHash = visitorIdentity.ipHash(request)
        if (!rateLimiter.tryAcquire("like:$ipHash", limit = LIKE_RATE_LIMIT, windowMillis = RATE_WINDOW_MS)) {
            throw SystemException.businessRuleViolation("操作太频繁，请稍后再试")
        }
        val visitorKeyHash = visitorIdentity.visitorKeyHash(request)
        if (!likeRepository.existsByPostIdAndVisitorKeyHash(post.id, visitorKeyHash)) {
            try {
                likeRepository.save(
                    BlogPostLike(
                        id = IdGenerator.nextId(),
                        postId = post.id,
                        visitorKeyHash = visitorKeyHash,
                    ),
                )
                statsRepository.adjustLike(post.id, +1)
            } catch (_: org.springframework.dao.DuplicateKeyException) {
                // 并发重复点赞：唯一约束兜底，幂等处理
            }
        }
        return currentStatus(post.id, visitorKeyHash)
    }

    /**
     * @description 取消点赞（幂等：未点赞直接返回当前状态）
     * @param slug 文章 slug
     * @param request HTTP 请求（派生访客标识）
     * @return LikeStatusDto 点赞数与当前访客点赞状态
     */
    @Transactional
    fun unlike(slug: String, request: HttpServletRequest): LikeStatusDto {
        val post = postService.requirePublicPost(slug)
        val ipHash = visitorIdentity.ipHash(request)
        if (!rateLimiter.tryAcquire("unlike:$ipHash", limit = LIKE_RATE_LIMIT, windowMillis = RATE_WINDOW_MS)) {
            throw SystemException.businessRuleViolation("操作太频繁，请稍后再试")
        }
        val visitorKeyHash = visitorIdentity.visitorKeyHash(request)
        if (likeRepository.existsByPostIdAndVisitorKeyHash(post.id, visitorKeyHash)) {
            likeRepository.deleteByPostIdAndVisitorKeyHash(post.id, visitorKeyHash)
            statsRepository.adjustLike(post.id, -1)
        }
        return currentStatus(post.id, visitorKeyHash)
    }

    /**
     * @description 组装当前点赞状态
     * @param postId 文章 ID
     * @param visitorKeyHash 访客标识哈希
     * @return LikeStatusDto 点赞数与状态
     */
    private fun currentStatus(postId: String, visitorKeyHash: String): LikeStatusDto =
        LikeStatusDto(
            count = statsRepository.findById(postId).map { it.likeCount }.orElse(0),
            liked = likeRepository.existsByPostIdAndVisitorKeyHash(postId, visitorKeyHash),
        )

    companion object {
        private const val VIEW_RATE_LIMIT = 60
        private const val LIKE_RATE_LIMIT = 30
        private const val RATE_WINDOW_MS = 60 * 1000L
    }
}
