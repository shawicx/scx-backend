package com.scx.backend.blog.comment

import com.scx.backend.blog.comment.dto.AdminCommentQueryDto
import com.scx.backend.blog.comment.dto.CommentAdminListResponseDto
import com.scx.backend.blog.security.AuthGuards
import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客评论管理控制器（管理员，审核/拒绝/删除）
 */
@Tag(name = "博客管理-评论", description = "评论列表与审核/拒绝/删除（需管理员）")
@RestController
@RequestMapping("/blog/admin/comments", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogCommentAdminController(
    private val commentService: BlogCommentService,
) {

    @Operation(summary = "评论管理列表", description = "全状态评论，提交时间倒序；支持状态/文章/关键字过滤")
    @GetMapping
    fun list(dto: AdminCommentQueryDto, request: HttpServletRequest): CommentAdminListResponseDto {
        AuthGuards.requireAdmin(request)
        return commentService.adminList(dto)
    }

    @OperationLog(module = "博客评论", action = "审核通过评论")
    @Operation(summary = "审核通过", description = "评论进入公开列表，评论数同步 +1")
    @PostMapping("/{id}/approve")
    fun approve(
        @Parameter(description = "评论 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        commentService.approve(id)
        return MessageDto("评论已通过")
    }

    @OperationLog(module = "博客评论", action = "审核拒绝评论")
    @Operation(summary = "审核拒绝", description = "评论不进入公开列表；原已通过时评论数同步 -1")
    @PostMapping("/{id}/reject")
    fun reject(
        @Parameter(description = "评论 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        commentService.reject(id)
        return MessageDto("评论已拒绝")
    }

    @OperationLog(module = "博客评论", action = "删除评论")
    @Operation(summary = "删除评论", description = "软删除；原已通过时评论数同步 -1")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "评论 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        commentService.delete(id)
        return MessageDto("评论已删除")
    }
}
