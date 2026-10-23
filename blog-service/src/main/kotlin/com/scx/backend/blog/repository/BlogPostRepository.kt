package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogPost
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * @description 博客文章仓库（公开列表/详情 + 管理端动态查询）
 */
interface BlogPostRepository : JpaRepository<BlogPost, String>, JpaSpecificationExecutor<BlogPost> {

    /**
     * @description 按 slug 查未删除文章（公开详情与管理编辑共用）
     * @param slug URL 标识
     * @return BlogPost? 未删除的文章
     */
    fun findFirstBySlugAndDeletedAtIsNull(slug: String): BlogPost?

    /**
     * @description 判断 slug 是否已被未删除文章占用
     * @param slug URL 标识
     * @return Boolean 是否占用
     */
    fun existsBySlugAndDeletedAtIsNull(slug: String): Boolean

    /**
     * @description 按 id 查未删除文章（管理端）
     * @param id 文章 ID
     * @return BlogPost? 未删除的文章
     */
    fun findFirstByIdAndDeletedAtIsNull(id: String): BlogPost?

    /**
     * @description 比当前文章发布更早的相邻公开文章（上一篇；同刻发布按 id 兜底排序）
     * @param publishedAt 当前文章发布时间
     * @param id 当前文章 ID
     * @param pageable 取第一条
     * @return List<BlogPost> 至多一条
     */
    @Query(
        """
        SELECT p FROM BlogPost p
        WHERE p.status = 1 AND p.visibility = 0 AND p.deletedAt IS NULL
          AND p.publishedAt <= :publishedAt AND p.id <> :id
        ORDER BY p.publishedAt DESC, p.id DESC
        """,
    )
    fun findPrevPublic(
        @Param("publishedAt") publishedAt: java.time.LocalDateTime,
        @Param("id") id: String,
        pageable: Pageable,
    ): List<BlogPost>

    /**
     * @description 比当前文章发布更晚的相邻公开文章（下一篇）
     * @param publishedAt 当前文章发布时间
     * @param id 当前文章 ID
     * @param pageable 取第一条
     * @return List<BlogPost> 至多一条
     */
    @Query(
        """
        SELECT p FROM BlogPost p
        WHERE p.status = 1 AND p.visibility = 0 AND p.deletedAt IS NULL
          AND p.publishedAt >= :publishedAt AND p.id <> :id
        ORDER BY p.publishedAt ASC, p.id ASC
        """,
    )
    fun findNextPublic(
        @Param("publishedAt") publishedAt: java.time.LocalDateTime,
        @Param("id") id: String,
        pageable: Pageable,
    ): List<BlogPost>

    /**
     * @description 同分类相关公开文章（排除自身，发布时间倒序）
     * @param categoryId 分类 ID
     * @param id 自身文章 ID
     * @param pageable 取前 N 条
     * @return List<BlogPost> 相关文章列表
     */
    @Query(
        """
        SELECT p FROM BlogPost p
        WHERE p.categoryId = :categoryId AND p.id <> :id
          AND p.status = 1 AND p.visibility = 0 AND p.deletedAt IS NULL
        ORDER BY p.publishedAt DESC
        """,
    )
    fun findRelatedByCategory(
        @Param("categoryId") categoryId: String,
        @Param("id") id: String,
        pageable: Pageable,
    ): List<BlogPost>

    /**
     * @description 未删除文章总数（概览统计）
     * @return Long 总数
     */
    fun countByDeletedAtIsNull(): Long

    /**
     * @description 指定状态未删除文章数（概览统计）
     * @param status 文章状态
     * @return Long 数量
     */
    fun countByStatusAndDeletedAtIsNull(status: Int): Long

    /**
     * @description 分类下未删除文章数（分类删除拦截用）
     * @param categoryId 分类 ID
     * @return Long 文章数
     */
    fun countByCategoryIdAndDeletedAtIsNull(categoryId: String): Long
}
