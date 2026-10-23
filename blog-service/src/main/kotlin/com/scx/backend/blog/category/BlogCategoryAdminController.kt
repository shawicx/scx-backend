package com.scx.backend.blog.category

import com.scx.backend.blog.category.dto.CategoryAdminListResponseDto
import com.scx.backend.blog.category.dto.CategoryAdminResponseDto
import com.scx.backend.blog.category.dto.CreateCategoryDto
import com.scx.backend.blog.category.dto.UpdateCategoryDto
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
 * @description 博客分类管理控制器（管理员）
 *
 * 管理接口统一挂 /blog/admin 前缀（不在网关公开白名单内）。
 */
@Tag(name = "博客管理-分类", description = "分类 CRUD 与启用/停用（需管理员）")
@RestController
@RequestMapping("/blog/admin/categories", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogCategoryAdminController(
    private val categoryService: BlogCategoryService,
) {

    @Operation(summary = "分类列表", description = "全量分类（含停用），排序号升序，附文章计数")
    @GetMapping
    fun list(request: HttpServletRequest): CategoryAdminListResponseDto {
        AuthGuards.requireAdmin(request)
        return categoryService.adminList()
    }

    @OperationLog(module = "博客分类", action = "创建分类")
    @Operation(summary = "创建分类", description = "slug 缺省由名称生成；重复报 9007")
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @Valid @RequestBody dto: CreateCategoryDto,
        request: HttpServletRequest,
    ): CategoryAdminResponseDto {
        AuthGuards.requireAdmin(request)
        return categoryService.create(dto)
    }

    @OperationLog(module = "博客分类", action = "更新分类")
    @Operation(summary = "更新分类", description = "仅更新元数据；slug 变更后需刷新对应公开页面")
    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @Parameter(description = "分类 ID", required = true) @PathVariable id: String,
        @Valid @RequestBody dto: UpdateCategoryDto,
        request: HttpServletRequest,
    ): CategoryAdminResponseDto {
        AuthGuards.requireAdmin(request)
        return categoryService.update(id, dto)
    }

    @OperationLog(module = "博客分类", action = "删除分类")
    @Operation(summary = "删除分类", description = "仍有文章时拒绝删除（9012），要求先迁移文章")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "分类 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        categoryService.delete(id)
        return MessageDto("分类已删除")
    }
}
