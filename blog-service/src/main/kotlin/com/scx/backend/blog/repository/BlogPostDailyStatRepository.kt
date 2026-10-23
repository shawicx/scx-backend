package com.scx.backend.blog.repository

import com.scx.backend.blog.entity.BlogPostDailyStat
import com.scx.backend.blog.entity.BlogPostDailyStatKey
import com.scx.backend.blog.stats.dto.DailyTrendRow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * @description 文章每日阅读统计仓库（当日计数 + 趋势聚合）
 */
interface BlogPostDailyStatRepository : JpaRepository<BlogPostDailyStat, BlogPostDailyStatKey> {

    /**
     * @description 当日阅读数原子 +1（行不存在时由服务层先插入）
     * @param postId 文章 ID
     * @param statDate 统计日期
     * @return Int 受影响行数
     */
    @Modifying
    @Query(
        """
        UPDATE BlogPostDailyStat d SET d.viewCount = d.viewCount + 1, d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.postId = :postId AND d.statDate = :statDate
        """,
    )
    fun incrementView(
        @Param("postId") postId: String,
        @Param("statDate") statDate: LocalDate,
    ): Int

    /**
     * @description 指定日期全站阅读总数（概览"今日阅读"）
     * @param statDate 统计日期
     * @return Long 阅读总数
     */
    @Query(
        """
        SELECT coalesce(sum(d.viewCount), 0) FROM BlogPostDailyStat d
        WHERE d.statDate = :statDate
        """,
    )
    fun sumViewCountByDate(@Param("statDate") statDate: LocalDate): Long

    /**
     * @description 近 N 天每日阅读趋势（含空档日由服务层补零）
     * @param since 起始日期（含）
     * @return List<DailyTrendRow> 日期-阅读数序列
     */
    @Query(
        """
        SELECT new com.scx.backend.blog.stats.dto.DailyTrendRow(d.statDate, sum(d.viewCount))
        FROM BlogPostDailyStat d
        WHERE d.statDate >= :since
        GROUP BY d.statDate
        ORDER BY d.statDate ASC
        """,
    )
    fun findTrendSince(@Param("since") since: LocalDate): List<DailyTrendRow>
}
