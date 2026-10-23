package com.scx.backend.blog.stats

import com.scx.backend.blog.security.AuthGuards
import com.scx.backend.blog.stats.dto.BlogOverviewResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客统计管理控制器（管理员概览）
 */
@Tag(name = "博客管理-统计", description = "博客概览统计（需管理员）")
@RestController
@RequestMapping("/blog/admin/stats", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogStatsAdminController(
    private val statsService: BlogStatsService,
) {

    @Operation(summary = "概览统计", description = "文章/阅读/点赞/评论计数、最近文章、最新评论与近 7 天阅读趋势")
    @GetMapping("/overview")
    fun overview(request: HttpServletRequest): BlogOverviewResponseDto {
        AuthGuards.requireAdmin(request)
        return statsService.overview()
    }
}
