package com.scx.backend.blog.tag

import com.scx.backend.blog.tag.dto.TagPublicListResponseDto
import com.scx.backend.common.decorator.Public
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客公开标签控制器（匿名可访问，经网关白名单放行）
 */
@Tag(name = "博客公开-标签", description = "启用标签列表（无需登录）")
@Public
@RestController
@RequestMapping("/blog/tags", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogTagPublicController(
    private val tagService: BlogTagService,
) {

    @Operation(summary = "公开标签列表", description = "仅启用标签，排序号升序，附公开文章数")
    @GetMapping
    fun list(): TagPublicListResponseDto = tagService.publicList()
}
