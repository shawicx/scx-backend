package com.scx.backend.blog.post

import com.scx.backend.blog.post.dto.PostPublicDetailDto
import com.scx.backend.blog.post.dto.PostPublicListResponseDto
import com.scx.backend.blog.post.dto.PublicPostQueryDto
import com.scx.backend.blog.post.dto.RelatedPostsResponseDto
import com.scx.backend.common.decorator.Public
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客公开文章控制器（匿名可访问，经网关白名单放行）
 *
 * 仅返回已发布、公开、未删除且发布时间已到的文章；草稿/私密/归档统一 404。
 */
@Tag(name = "博客公开-文章", description = "公开文章列表、详情与相关文章（无需登录）")
@Public
@RestController
@RequestMapping("/blog/posts", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogPostPublicController(
    private val postService: BlogPostService,
) {

    @Operation(summary = "公开文章列表", description = "仅已发布公开文章；置顶优先、发布时间倒序；支持分类/标签 slug 与关键字过滤")
    @GetMapping
    fun list(dto: PublicPostQueryDto): PostPublicListResponseDto = postService.publicList(dto)

    @Operation(summary = "文章详情", description = "按 slug 查公开文章（含正文、统计与上/下一篇）；草稿与私密文章返回 404")
    @GetMapping("/{slug}")
    fun detail(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
    ): PostPublicDetailDto = postService.publicDetail(slug)

    @Operation(summary = "相关文章", description = "同分类公开文章（无分类时回退最新公开文章），最多 5 条")
    @GetMapping("/{slug}/related")
    fun related(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
    ): RelatedPostsResponseDto = postService.related(slug)
}
