package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * @description 博客分类仓库（slug 唯一性 + 启用列表 + 公开文章计数）
 */
interface BlogCategoryRepository : JpaRepository<BlogCategory, String> {

    /**
     * @description 按 slug 查分类（公开列表按分类过滤时解析 slug 用）
     * @param slug URL 标识
     * @return BlogCategory? 分类
     */
    fun findFirstBySlug(slug: String): BlogCategory?

    /**
     * @description 判断 slug 是否已占用
     * @param slug URL 标识
     * @return Boolean 是否占用
     */
    fun existsBySlug(slug: String): Boolean

    /**
     * @description 管理端全量分类（排序号升序）
     * @return List<BlogCategory> 分类列表
     */
    fun findAllByOrderBySortAscCreatedAtAsc(): List<BlogCategory>

    /**
     * @description 公开分类列表（仅启用，排序号升序）
     * @return List<BlogCategory> 启用分类列表
     */
    fun findByStatusOrderBySortAscCreatedAtAsc(status: Int): List<BlogCategory>

    /**
     * @description 分类下公开可见文章数（公开分类列表展示用）
     * @param categoryId 分类 ID
     * @return Long 公开文章数
     */
    @Query(
        """
        SELECT count(p) FROM BlogPost p
        WHERE p.categoryId = :categoryId AND p.deletedAt IS NULL
          AND p.status = 1 AND p.visibility = 0
        """,
    )
    fun countPublicPostsByCategoryId(@Param("categoryId") categoryId: String): Long
}
