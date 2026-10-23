package com.scx.backend.blog.tag

import com.scx.backend.blog.security.AuthGuards
import com.scx.backend.blog.tag.dto.CreateTagDto
import com.scx.backend.blog.tag.dto.TagAdminListResponseDto
import com.scx.backend.blog.tag.dto.TagAdminResponseDto
import com.scx.backend.blog.tag.dto.UpdateTagDto
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
 * @description 博客标签管理控制器（管理员）
 *
 * 管理接口统一挂 /blog/admin 前缀（不在网关公开白名单内）。
 */
@Tag(name = "博客管理-标签", description = "标签 CRUD 与启用/停用（需管理员）")
@RestController
@RequestMapping("/blog/admin/tags", produces = [MediaType.APPLICATION_JSON_VALUE])
class BlogTagAdminController(
    private val tagService: BlogTagService,
) {

    @Operation(summary = "标签列表", description = "全量标签（含停用），排序号升序，附公开文章计数")
    @GetMapping
    fun list(request: HttpServletRequest): TagAdminListResponseDto {
        AuthGuards.requireAdmin(request)
        return tagService.adminList()
    }

    @OperationLog(module = "博客标签", action = "创建标签")
    @Operation(summary = "创建标签", description = "slug 缺省由名称生成；重复报 9007")
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        @Valid @RequestBody dto: CreateTagDto,
        request: HttpServletRequest,
    ): TagAdminResponseDto {
        AuthGuards.requireAdmin(request)
        return tagService.create(dto)
    }

    @OperationLog(module = "博客标签", action = "更新标签")
    @Operation(summary = "更新标签", description = "仅更新元数据；slug 变更后需刷新对应公开页面")
    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @Parameter(description = "标签 ID", required = true) @PathVariable id: String,
        @Valid @RequestBody dto: UpdateTagDto,
        request: HttpServletRequest,
    ): TagAdminResponseDto {
        AuthGuards.requireAdmin(request)
        return tagService.update(id, dto)
    }

    @OperationLog(module = "博客标签", action = "删除标签")
    @Operation(summary = "删除标签", description = "连同文章关联关系一并清理")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "标签 ID", required = true) @PathVariable id: String,
        request: HttpServletRequest,
    ): MessageDto {
        AuthGuards.requireAdmin(request)
        tagService.delete(id)
        return MessageDto("标签已删除")
    }
}
