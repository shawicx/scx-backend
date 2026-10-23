package com.scx.backend.blog.comment

import com.scx.backend.blog.comment.dto.AdminCommentQueryDto
import com.scx.backend.blog.comment.dto.CommentAdminListItemDto
import com.scx.backend.blog.comment.dto.CommentAdminListResponseDto
import com.scx.backend.blog.comment.dto.CommentPublicListResponseDto
import com.scx.backend.blog.comment.dto.CommentPublicResponseDto
import com.scx.backend.blog.comment.dto.CreateCommentDto
import com.scx.backend.blog.comment.dto.PublicCommentQueryDto
import com.scx.backend.blog.entity.BlogComment
import com.scx.backend.blog.post.BlogPostService
import com.scx.backend.blog.repository.BlogCommentRepository
import com.scx.backend.blog.repository.BlogPostRepository
import com.scx.backend.blog.repository.BlogPostStatsRepository
import com.scx.backend.blog.support.BlogRateLimiter
import com.scx.backend.blog.support.VisitorIdentity
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import jakarta.persistence.criteria.Predicate
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description 博客评论服务（匿名提交先审后显 + 管理端审核/删除）
 *
 * 提交不要求登录：昵称必填、邮箱选填；同一 IP 有频率限制（尽力而为）；
 * 评论内容按纯文本落库，公开响应不含邮箱/IP/访客标识。
 * 评论数统计只计入已审核通过且未删除的评论，审核/删除时事务内同步维护。
 */
@Service
class BlogCommentService(
    private val commentRepository: BlogCommentRepository,
    private val postRepository: BlogPostRepository,
    private val postService: BlogPostService,
    private val statsRepository: BlogPostStatsRepository,
    private val visitorIdentity: VisitorIdentity,
    private val rateLimiter: BlogRateLimiter,
) {

    /**
     * @description 匿名发表评论（默认待审核；限流超限报 9012）
     * @param slug 文章 slug
     * @param dto 提交请求
     * @param request HTTP 请求（派生访客/IP 标识）
     */
    @Transactional
    fun submit(slug: String, dto: CreateCommentDto, request: HttpServletRequest) {
        val post = postService.requirePublicPost(slug)
        if (!post.allowComment) {
            throw SystemException.businessRuleViolation("该文章已关闭评论")
        }
        val ipHash = visitorIdentity.ipHash(request)
        if (!rateLimiter.tryAcquire("comment:$ipHash", limit = COMMENT_RATE_LIMIT, windowMillis = COMMENT_RATE_WINDOW_MS)) {
            throw SystemException.businessRuleViolation("评论太频繁，请稍后再试")
        }

        var parentId: String? = null
        var rootId: String? = null
        dto.parentId?.takeIf { it.isNotBlank() }?.let { pid ->
            val parent = commentRepository.findFirstByIdAndDeletedAtIsNull(pid)
                ?: throw SystemException.invalidParameter("回复的评论不存在")
            if (parent.postId != post.id) {
                throw SystemException.invalidParameter("回复的评论不属于该文章")
            }
            if (parent.status != BlogComment.STATUS_APPROVED) {
                throw SystemException.invalidParameter("只能回复已通过的评论")
            }
            parentId = parent.id
            rootId = parent.rootId ?: parent.id
        }

        commentRepository.save(
            BlogComment(
                id = IdGenerator.nextId(),
                postId = post.id,
                parentId = parentId,
                rootId = rootId,
                nickname = dto.nickname.trim(),
                email = dto.email?.trim()?.takeIf { it.isNotEmpty() },
                website = dto.website?.trim()?.takeIf { it.isNotEmpty() },
                content = dto.content.trim(),
                status = BlogComment.STATUS_PENDING,
                visitorKeyHash = visitorIdentity.visitorKeyHash(request),
                ipHash = ipHash,
            ),
        )
    }

    /**
     * @description 文章已审核评论分页（公开；时间正序；不含私有字段）
     * @param slug 文章 slug
     * @param dto 分页参数
     * @return CommentPublicListResponseDto 评论分页
     */
    @Transactional(readOnly = true)
    fun publicList(slug: String, dto: PublicCommentQueryDto): CommentPublicListResponseDto {
        val post = postService.requirePublicPost(slug)
        val page = commentRepository.findByPostIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            post.id,
            BlogComment.STATUS_APPROVED,
            PageRequest.of(dto.page - 1, dto.limit),
        )
        return CommentPublicListResponseDto(
            list = page.content.map {
                CommentPublicResponseDto(
                    id = it.id,
                    nickname = it.nickname,
                    website = it.website,
                    content = it.content,
                    parentId = it.parentId,
                    rootId = it.rootId,
                    createdAt = it.createdAt,
                )
            },
            total = page.totalElements,
            page = dto.page,
            limit = dto.limit,
        )
    }

    /**
     * @description 管理端评论分页（全状态；支持文章/状态/关键字过滤）
     * @param dto 查询参数
     * @return CommentAdminListResponseDto 评论分页
     */
    @Transactional(readOnly = true)
    fun adminList(dto: AdminCommentQueryDto): CommentAdminListResponseDto {
        val spec = Specification<BlogComment> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.isNull(root.get<LocalDateTime>("deletedAt")))
            dto.status?.let { predicates.add(cb.equal(root.get<Int>("status"), it)) }
            dto.postId?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.equal(root.get<String>("postId"), it))
            }
            dto.search?.takeIf { it.isNotBlank() }?.let { kw ->
                val pattern = "%${kw.lowercase()}%"
                cb.or(
                    cb.like(cb.lower(root.get("nickname")), pattern),
                    cb.like(cb.lower(root.get("content")), pattern),
                )
            }?.let { predicates.add(it) }
            cb.and(*predicates.toTypedArray())
        }
        val page = commentRepository.findAll(
            spec,
            PageRequest.of(dto.page - 1, dto.limit, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        val titles = page.content.map { it.postId }.distinct()
            .let { postRepository.findAllById(it) }
            .associate { it.id to it.title }
        return CommentAdminListResponseDto(
            list = page.content.map { CommentAdminListResponseDto.itemOf(it, titles[it.postId] ?: "") },
            total = page.totalElements,
            page = dto.page,
            limit = dto.limit,
        )
    }

    /**
     * @description 审核通过评论（待审核/已拒绝 → 已通过；评论数 +1 仅在原状态非通过时）
     * @param id 评论 ID
     */
    @Transactional
    fun approve(id: String) {
        val comment = requireComment(id)
        val wasApproved = comment.status == BlogComment.STATUS_APPROVED
        comment.status = BlogComment.STATUS_APPROVED
        comment.updatedAt = LocalDateTime.now()
        commentRepository.save(comment)
        if (!wasApproved) {
            statsRepository.adjustComment(comment.postId, +1)
        }
    }

    /**
     * @description 审核拒绝评论（原状态为已通过时评论数 -1）
     * @param id 评论 ID
     */
    @Transactional
    fun reject(id: String) {
        val comment = requireComment(id)
        val wasApproved = comment.status == BlogComment.STATUS_APPROVED
        comment.status = BlogComment.STATUS_REJECTED
        comment.updatedAt = LocalDateTime.now()
        commentRepository.save(comment)
        if (wasApproved) {
            statsRepository.adjustComment(comment.postId, -1)
        }
    }

    /**
     * @description 删除评论（软删除；原状态为已通过时评论数 -1）
     * @param id 评论 ID
     */
    @Transactional
    fun delete(id: String) {
        val comment = requireComment(id)
        val wasApproved = comment.status == BlogComment.STATUS_APPROVED
        val now = LocalDateTime.now()
        comment.deletedAt = now
        comment.updatedAt = now
        commentRepository.save(comment)
        if (wasApproved) {
            statsRepository.adjustComment(comment.postId, -1)
        }
    }

    /**
     * @description 查找未删除评论，不存在抛 9002
     * @param id 评论 ID
     * @return BlogComment 评论实体
     */
    private fun requireComment(id: String): BlogComment =
        commentRepository.findFirstByIdAndDeletedAtIsNull(id)
            ?: throw SystemException.dataNotFound("评论不存在")

    companion object {
        /** 评论频率限制：同一 IP 10 分钟内最多 5 条 */
        private const val COMMENT_RATE_LIMIT = 5
        private const val COMMENT_RATE_WINDOW_MS = 10 * 60 * 1000L
    }
}
