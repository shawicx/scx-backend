package com.scx.backend.blog.category.dto

import com.scx.backend.blog.entity.BlogCategory
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * @description 博客分类相关 DTO（管理端 CRUD + 公开列表）
 */

/** 创建分类请求 */
@Schema(description = "创建分类请求")
data class CreateCategoryDto(
    @Schema(description = "分类名称", required = true)
    @field:NotBlank(message = "分类名称不能为空")
    @field:Size(max = 80, message = "分类名称不能超过80个字符")
    val name: String,

    @Schema(description = "URL 标识（缺省时由名称生成）")
    @field:Size(max = 100, message = "slug不能超过100个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "分类描述")
    @field:Size(max = 500, message = "分类描述不能超过500个字符")
    val description: String? = null,

    @Schema(description = "排序号（越小越靠前，缺省 0）")
    val sort: Int? = 0,

    @Schema(description = "状态（0 停用 / 1 启用，缺省启用）", defaultValue = "1")
    val status: Int? = 1,
)

/** 更新分类请求 */
@Schema(description = "更新分类请求")
data class UpdateCategoryDto(
    @Schema(description = "分类名称", required = true)
    @field:NotBlank(message = "分类名称不能为空")
    @field:Size(max = 80, message = "分类名称不能超过80个字符")
    val name: String,

    @Schema(description = "URL 标识")
    @field:Size(max = 100, message = "slug不能超过100个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "分类描述")
    @field:Size(max = 500, message = "分类描述不能超过500个字符")
    val description: String? = null,

    @Schema(description = "排序号（缺省 0）")
    val sort: Int? = 0,

    @Schema(description = "状态（0 停用 / 1 启用）")
    val status: Int? = 1,
)

/** 管理端分类响应（含未删除文章数） */
@Schema(description = "管理端分类响应")
data class CategoryAdminResponseDto(
    @Schema(description = "分类 ID") val id: String,
    @Schema(description = "分类名称") val name: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "分类描述") val description: String?,
    @Schema(description = "排序号") val sort: Int,
    @Schema(description = "状态（0 停用 / 1 启用）") val status: Int,
    @Schema(description = "未删除文章数") val postCount: Long,
    @Schema(description = "公开可见文章数") val publicPostCount: Long,
) {
    companion object {
        /**
         * @description 实体转管理端响应 DTO
         * @param entity 分类实体
         * @param postCount 未删除文章数
         * @param publicPostCount 公开可见文章数
         * @return CategoryAdminResponseDto 管理端响应
         */
        fun from(entity: BlogCategory, postCount: Long, publicPostCount: Long): CategoryAdminResponseDto =
            CategoryAdminResponseDto(
                id = entity.id,
                name = entity.name,
                slug = entity.slug,
                description = entity.description,
                sort = entity.sort,
                status = entity.status,
                postCount = postCount,
                publicPostCount = publicPostCount,
            )
    }
}

/** 管理端分类列表响应 */
@Schema(description = "管理端分类列表响应")
data class CategoryAdminListResponseDto(
    @Schema(description = "分类列表（排序号升序）") val list: List<CategoryAdminResponseDto>,
)

/** 公开分类响应 */
@Schema(description = "公开分类响应")
data class CategoryPublicResponseDto(
    @Schema(description = "分类名称") val name: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "分类描述") val description: String?,
    @Schema(description = "公开可见文章数") val postCount: Long,
)

/** 公开分类列表响应 */
@Schema(description = "公开分类列表响应")
data class CategoryPublicListResponseDto(
    @Schema(description = "分类列表（仅启用，排序号升序）") val list: List<CategoryPublicResponseDto>,
)
