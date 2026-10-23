package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogTag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * @description 博客标签仓库（slug 唯一性 + 启用列表 + 公开文章计数）
 */
interface BlogTagRepository : JpaRepository<BlogTag, String> {

    /**
     * @description 按 slug 查标签（公开列表按标签过滤时解析 slug 用）
     * @param slug URL 标识
     * @return BlogTag? 标签
     */
    fun findFirstBySlug(slug: String): BlogTag?

    /**
     * @description 判断 slug 是否已占用
     * @param slug URL 标识
     * @return Boolean 是否占用
     */
    fun existsBySlug(slug: String): Boolean

    /**
     * @description 管理端全量标签（排序号升序）
     * @return List<BlogTag> 标签列表
     */
    fun findAllByOrderBySortAscCreatedAtAsc(): List<BlogTag>

    /**
     * @description 公开标签列表（仅启用，排序号升序）
     * @return List<BlogTag> 启用标签列表
     */
    fun findByStatusOrderBySortAscCreatedAtAsc(status: Int): List<BlogTag>

    /**
     * @description 标签关联的公开可见文章数（公开标签列表展示用）
     * @param tagId 标签 ID
     * @return Long 公开文章数
     */
    @Query(
        """
        SELECT count(p) FROM BlogPost p
        WHERE p.deletedAt IS NULL AND p.status = 1 AND p.visibility = 0
          AND p.id IN (SELECT pt.postId FROM BlogPostTag pt WHERE pt.tagId = :tagId)
        """,
    )
    fun countPublicPostsByTagId(@Param("tagId") tagId: String): Long
}
