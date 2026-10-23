package com.scx.backend.blog.post

import com.scx.backend.blog.post.dto.AdminPostQueryDto
import com.scx.backend.blog.post.dto.CreatePostDto
import com.scx.backend.blog.post.dto.PostAdminListResponseDto
import com.scx.backend.blog.post.dto.PostDetailResponseDto
import com.scx.backend.blog.post.dto.SetPostTopDto
import com.scx.backend.blog.post.dto.UpdatePostDto
import com.scx.backend.blog.security.AuthGuards
import com.scx.backend.common.dto.MessageDto
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客文章管理控制器（管理员，经网关 RBAC 校验后转发）
 *
 * 管理接口统一挂 /blog/admin 前缀（不在网关公开白名单内）；
 * 身份经网关注入的 X-User-* 头解析，写操作要求管理员。
 */
@Tag(name = "博客管理-文章", description = "文章 CRUD、发布/撤回/归档与置顶（需管理员）")
@RestController
@RequestMapping("/blog/admin/posts", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogPostAdminController(
    private val postService: BlogPostService,
) {

    @Operation(summary = "文章管理列表", description = "含草稿/私密/归档；支持关键字、状态、分类、置顶过滤与排序")
    @GetMapping
    fun list(dto: AdminPostQueryDto, request: HttpServletRequest): PostAdminListResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.adminList(dto)
    }

    @Operation(summary = "文章详情", description = "编辑回显数据（含标签 ID 列表）")
    @GetMapping("/{id}")
    fun detail(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.adminDetail(id)
    }

    @OperationLog(module = "博客文章", action = "创建文章")
    @Operation(summary = "创建文章", description = "创建后为草稿状态；发布走独立接口")
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @Valid @RequestBody dto: CreatePostDto,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        val principal = AuthGuards.requireAdmin(request)
        return postService.create(dto, principal.userId)
    }

    @OperationLog(module = "博客文章", action = "更新文章")
    @Operation(summary = "更新文章", description = "不改变发布状态；修改已发布文章的 slug 需自行处理旧链接重定向")
    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        @Valid @RequestBody dto: UpdatePostDto,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.update(id, dto)
    }

    @OperationLog(module = "博客文章", action = "发布文章")
    @Operation(summary = "发布文章", description = "草稿/归档 → 已发布；首次发布落发布时间并触发公开可见")
    @PostMapping("/{id}/publish")
    fun publish(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.publish(id)
    }

    @OperationLog(module = "博客文章", action = "撤回发布")
    @Operation(summary = "撤回发布", description = "已发布 → 草稿；公开接口立即不可见（保留首次发布时间）")
    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.withdraw(id)
    }

    @OperationLog(module = "博客文章", action = "归档文章")
    @Operation(summary = "归档文章", description = "任意状态 → 已归档；公开接口按 404 处理")
    @PostMapping("/{id}/archive")
    fun archive(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.archive(id)
    }

    @OperationLog(module = "博客文章", action = "设置置顶")
    @Operation(summary = "设置置顶", description = "置顶文章在公开列表优先展示")
    @PostMapping("/{id}/top", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun setTop(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        @Valid @RequestBody dto: SetPostTopDto,
        request: HttpServletRequest,
    ): PostDetailResponseDto {
        AuthGuards.requireAdmin(request)
        return postService.setTop(id, dto)
    }

    @OperationLog(module = "博客文章", action = "删除文章")
    @Operation(summary = "删除文章", description = "软删除；slug 保留占用，统计与标签关联保留")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "文章 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        postService.delete(id)
        return MessageDto("文章已删除")
    }
}
