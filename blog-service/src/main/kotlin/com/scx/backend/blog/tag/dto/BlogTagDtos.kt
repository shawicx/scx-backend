package com.scx.backend.blog.tag.dto

import com.scx.backend.blog.entity.BlogTag
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * @description 博客标签相关 DTO（管理端 CRUD + 公开列表）
 */

/** 创建标签请求 */
@Schema(description = "创建标签请求")
data class CreateTagDto(
    @Schema(description = "标签名称", required = true)
    @field:NotBlank(message = "标签名称不能为空")
    @field:Size(max = 60, message = "标签名称不能超过60个字符")
    val name: String,

    @Schema(description = "URL 标识（缺省时由名称生成）")
    @field:Size(max = 80, message = "slug不能超过80个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "标签描述")
    @field:Size(max = 255, message = "标签描述不能超过255个字符")
    val description: String? = null,

    @Schema(description = "排序号（越小越靠前，缺省 0）")
    val sort: Int? = 0,

    @Schema(description = "状态（0 停用 / 1 启用，缺省启用）", defaultValue = "1")
    val status: Int? = 1,
)

/** 更新标签请求 */
@Schema(description = "更新标签请求")
data class UpdateTagDto(
    @Schema(description = "标签名称", required = true)
    @field:NotBlank(message = "标签名称不能为空")
    @field:Size(max = 60, message = "标签名称不能超过60个字符")
    val name: String,

    @Schema(description = "URL 标识")
    @field:Size(max = 80, message = "slug不能超过80个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "标签描述")
    @field:Size(max = 255, message = "标签描述不能超过255个字符")
    val description: String? = null,

    @Schema(description = "排序号（缺省 0）")
    val sort: Int? = 0,

    @Schema(description = "状态（0 停用 / 1 启用）")
    val status: Int? = 1,
)

/** 管理端标签响应 */
@Schema(description = "管理端标签响应")
data class TagAdminResponseDto(
    @Schema(description = "标签 ID") val id: String,
    @Schema(description = "标签名称") val name: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "标签描述") val description: String?,
    @Schema(description = "排序号") val sort: Int,
    @Schema(description = "状态（0 停用 / 1 启用）") val status: Int,
    @Schema(description = "公开可见文章数") val publicPostCount: Long,
) {
    companion object {
        /**
         * @description 实体转管理端响应 DTO
         * @param entity 标签实体
         * @param publicPostCount 公开可见文章数
         * @return TagAdminResponseDto 管理端响应
         */
        fun from(entity: BlogTag, publicPostCount: Long): TagAdminResponseDto = TagAdminResponseDto(
            id = entity.id,
            name = entity.name,
            slug = entity.slug,
            description = entity.description,
            sort = entity.sort,
            status = entity.status,
            publicPostCount = publicPostCount,
        )
    }
}

/** 管理端标签列表响应 */
@Schema(description = "管理端标签列表响应")
data class TagAdminListResponseDto(
    @Schema(description = "标签列表（排序号升序）") val list: List<TagAdminResponseDto>,
)

/** 公开标签响应 */
@Schema(description = "公开标签响应")
data class TagPublicResponseDto(
    @Schema(description = "标签名称") val name: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "标签描述") val description: String?,
    @Schema(description = "公开可见文章数") val postCount: Long,
)

/** 公开标签列表响应 */
@Schema(description = "公开标签列表响应")
data class TagPublicListResponseDto(
    @Schema(description = "标签列表（仅启用，排序号升序）") val list: List<TagPublicResponseDto>,
)
