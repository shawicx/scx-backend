package com.scx.backend.blog.stats.dto

import java.time.LocalDate

/**
 * @description 每日阅读趋势行（JPQL 构造表达式投影）
 *
 * @property date 统计日期
 * @property views 当日全站阅读数
 */
data class DailyTrendRow(
    val date: LocalDate,
    val views: Long,
)
