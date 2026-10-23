package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogComment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * @description 博客评论仓库（公开已审列表 + 管理端动态查询 + 审核计数）
 */
interface BlogCommentRepository : JpaRepository<BlogComment, String>, JpaSpecificationExecutor<BlogComment> {

    /**
     * @description 文章已审核通过且未删除的评论分页（时间正序，公开接口）
     * @param postId 文章 ID
     * @param status 审核状态
     * @param pageable 分页
     * @return Page<BlogComment> 评论页
     */
    fun findByPostIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
        postId: String,
        status: Int,
        pageable: Pageable,
    ): Page<BlogComment>

    /**
     * @description 文章已审核通过且未删除的评论数（统计维护）
     * @param postId 文章 ID
     * @param status 审核状态
     * @return Long 评论数
     */
    fun countByPostIdAndStatusAndDeletedAtIsNull(postId: String, status: Int): Long

    /**
     * @description 待审核评论数（概览统计）
     * @param status 审核状态
     * @return Long 数量
     */
    fun countByStatusAndDeletedAtIsNull(status: Int): Long

    /**
     * @description 按 id 查未删除评论
     * @param id 评论 ID
     * @return BlogComment? 未删除评论
     */
    fun findFirstByIdAndDeletedAtIsNull(id: String): BlogComment?
}
