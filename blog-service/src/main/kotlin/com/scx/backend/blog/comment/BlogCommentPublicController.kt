package com.scx.backend.blog.comment

import com.scx.backend.blog.comment.dto.CommentPublicListResponseDto
import com.scx.backend.blog.comment.dto.CreateCommentDto
import com.scx.backend.blog.comment.dto.PublicCommentQueryDto
import com.scx.backend.common.decorator.Public
import com.scx.backend.common.dto.MessageDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客公开评论控制器（匿名可访问，经网关白名单放行）
 *
 * 提交评论无需登录（昵称必填），默认待审核；公开列表仅返回已通过评论。
 */
@Tag(name = "博客公开-评论", description = "文章已审核评论列表与匿名发表（无需登录）")
@Public
@RestController
@RequestMapping("/blog/posts/{slug}/comments", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogCommentPublicController(
    private val commentService: BlogCommentService,
) {

    @Operation(summary = "已审核评论列表", description = "仅审核通过且未删除的评论，时间正序；文章不存在返回 404")
    @GetMapping
    fun list(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
        dto: PublicCommentQueryDto,
    ): CommentPublicListResponseDto = commentService.publicList(slug, dto)

    @Operation(summary = "发表评论", description = "匿名提交（昵称必填、邮箱选填）；默认待审核，审核通过后展示")
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
        @Valid @RequestBody dto: CreateCommentDto,
        request: HttpServletRequest,
    ): MessageDto {
        commentService.submit(slug, dto, request)
        return MessageDto("评论已提交，审核通过后将展示")
    }
}
