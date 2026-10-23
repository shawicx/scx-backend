package com.scx.backend.blog.category

import com.scx.backend.blog.category.dto.CategoryPublicListResponseDto
import com.scx.backend.common.decorator.Public
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description 博客公开分类控制器（匿名可访问，经网关白名单放行）
 */
@Tag(name = "博客公开-分类", description = "启用分类列表（无需登录）")
@Public
@RestController
@RequestMapping("/blog/categories", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogCategoryPublicController(
    private val categoryService: BlogCategoryService,
) {

    @Operation(summary = "公开分类列表", description = "仅启用分类，排序号升序，附公开文章数")
    @GetMapping
    fun list(): CategoryPublicListResponseDto = categoryService.publicList()
}
