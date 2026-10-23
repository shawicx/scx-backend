package com.scx.backend.blog.interaction

import com.scx.backend.blog.interaction.dto.LikeStatusDto
import com.scx.backend.common.decorator.Public
import com.scx.backend.common.dto.MessageDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客公开互动控制器（匿名阅读上报与点赞，经网关白名单放行）
 *
 * 匿名身份由服务端从请求特征派生，不信任客户端提交的任何标识。
 */
@Tag(name = "博客公开-互动", description = "阅读上报与匿名点赞/取消点赞（无需登录）")
@Public
@RestController
@RequestMapping("/blog/posts/{slug}", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogInteractionPublicController(
    private val interactionService: BlogInteractionService,
) {

    @Operation(summary = "上报阅读", description = "文章详情加载成功后调用一次；超限静默忽略")
    @PostMapping("/views")
    fun recordView(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
        request: HttpServletRequest,
    ): MessageDto {
        interactionService.recordView(slug, request)
        return MessageDto("阅读已记录")
    }

    @Operation(summary = "点赞", description = "匿名访客按服务端标识去重，幂等；返回最新计数")
    @PostMapping("/like")
    fun like(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
        request: HttpServletRequest,
    ): LikeStatusDto = interactionService.like(slug, request)

    @Operation(summary = "取消点赞", description = "幂等；返回最新计数")
    @DeleteMapping("/like")
    fun unlike(
        @Parameter(description = "URL 标识", required = true) @PathVariable slug: String,
        request: HttpServletRequest,
    ): LikeStatusDto = interactionService.unlike(slug, request)
}
