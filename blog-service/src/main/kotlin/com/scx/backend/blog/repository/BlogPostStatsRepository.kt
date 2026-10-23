package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogPostStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * @description 文章聚合统计仓库（原子计数维护）
 */
interface BlogPostStatsRepository : JpaRepository<BlogPostStats, String> {

    /**
     * @description 阅读数原子 +1（并发安全）
     * @param postId 文章 ID
     * @return Int 受影响行数
     */
    @Modifying
    @Query(
        """
        UPDATE BlogPostStats s SET s.viewCount = s.viewCount + 1, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.postId = :postId
        """,
    )
    fun incrementView(@Param("postId") postId: String): Int

    /**
     * @description 点赞数原子增减（delta 为 +1 / -1）
     * @param postId 文章 ID
     * @param delta 增量
     * @return Int 受影响行数
     */
    @Modifying
    @Query(
        """
        UPDATE BlogPostStats s SET s.likeCount = s.likeCount + :delta, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.postId = :postId
        """,
    )
    fun adjustLike(@Param("postId") postId: String, @Param("delta") delta: Long): Int

    /**
     * @description 评论数原子增减（delta 为 +1 / -1）
     * @param postId 文章 ID
     * @param delta 增量
     * @return Int 受影响行数
     */
    @Modifying
    @Query(
        """
        UPDATE BlogPostStats s SET s.commentCount = s.commentCount + :delta, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.postId = :postId
        """,
    )
    fun adjustComment(@Param("postId") postId: String, @Param("delta") delta: Long): Int

    /**
     * @description 全站累计阅读数（概览统计）
     * @return Long 累计阅读数
     */
    @Query("SELECT coalesce(sum(s.viewCount), 0) FROM BlogPostStats s")
    fun sumAllViews(): Long

    /**
     * @description 全站当前有效点赞总数（概览统计）
     * @return Long 累计点赞数
     */
    @Query("SELECT coalesce(sum(s.likeCount), 0) FROM BlogPostStats s")
    fun sumAllLikes(): Long
}
