package com.scx.backend.blog.post.dto

import com.scx.backend.blog.entity.BlogPost
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * @description 博客文章相关 DTO（管理端 CRUD / 列表查询 / 公开列表与详情 / 相关文章）
 */

/** 标签简要信息（列表项内嵌） */
@Schema(description = "标签简要信息")
data class TagBriefDto(
    @Schema(description = "标签 ID") val id: String,
    @Schema(description = "标签名称") val name: String,
    @Schema(description = "标签 slug") val slug: String,
)

/** 分类简要信息（列表项/详情内嵌；未分类时为 null） */
@Schema(description = "分类简要信息")
data class CategoryBriefDto(
    @Schema(description = "分类 ID") val id: String,
    @Schema(description = "分类名称") val name: String,
    @Schema(description = "分类 slug") val slug: String,
)

/** 创建文章请求（创建后为草稿，发布走独立接口） */
@Schema(description = "创建文章请求")
data class CreatePostDto(
    @Schema(description = "文章标题", required = true)
    @field:NotBlank(message = "文章标题不能为空")
    @field:Size(max = 200, message = "文章标题不能超过200个字符")
    val title: String,

    @Schema(description = "URL 标识（缺省时由标题生成）")
    @field:Size(max = 220, message = "slug不能超过220个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "文章摘要")
    @field:Size(max = 500, message = "摘要不能超过500个字符")
    val summary: String? = null,

    @Schema(description = "Markdown 正文", required = true)
    @field:NotBlank(message = "文章正文不能为空")
    @field:Size(max = 200000, message = "文章正文过长")
    val content: String,

    @Schema(description = "封面文件 ID（file-service 文件标识）")
    @field:Size(max = 30, message = "封面文件ID不能超过30个字符")
    val coverFileId: String? = null,

    @Schema(description = "分类 ID")
    @field:Size(max = 30, message = "分类ID不能超过30个字符")
    val categoryId: String? = null,

    @Schema(description = "标签 ID 列表（缺省为空）")
    val tagIds: List<String>? = emptyList(),

    @Schema(description = "可见性（0 公开 / 1 私密，缺省公开）", defaultValue = "0")
    val visibility: Int? = BlogPost.VISIBILITY_PUBLIC,

    @Schema(description = "是否置顶", defaultValue = "false")
    val isTop: Boolean? = false,

    @Schema(description = "是否允许评论", defaultValue = "true")
    val allowComment: Boolean? = true,

    @Schema(description = "SEO 标题（空则回退文章标题）")
    @field:Size(max = 200, message = "SEO标题不能超过200个字符")
    val seoTitle: String? = null,

    @Schema(description = "SEO 描述（空则回退摘要）")
    @field:Size(max = 500, message = "SEO描述不能超过500个字符")
    val seoDescription: String? = null,
)

/** 更新文章请求（与创建同构；发布状态不经此接口变更） */
@Schema(description = "更新文章请求")
data class UpdatePostDto(
    @Schema(description = "文章标题", required = true)
    @field:NotBlank(message = "文章标题不能为空")
    @field:Size(max = 200, message = "文章标题不能超过200个字符")
    val title: String,

    @Schema(description = "URL 标识（修改已发布文章的 slug 需自行处理旧链接重定向）")
    @field:Size(max = 220, message = "slug不能超过220个字符")
    @field:Pattern(regexp = "^[a-z0-9-]*$", message = "slug只能包含小写字母、数字和连字符")
    val slug: String? = null,

    @Schema(description = "文章摘要")
    @field:Size(max = 500, message = "摘要不能超过500个字符")
    val summary: String? = null,

    @Schema(description = "Markdown 正文", required = true)
    @field:NotBlank(message = "文章正文不能为空")
    @field:Size(max = 200000, message = "文章正文过长")
    val content: String,

    @Schema(description = "封面文件 ID")
    @field:Size(max = 30, message = "封面文件ID不能超过30个字符")
    val coverFileId: String? = null,

    @Schema(description = "分类 ID（传空表示改为未分类）")
    @field:Size(max = 30, message = "分类ID不能超过30个字符")
    val categoryId: String? = null,

    @Schema(description = "标签 ID 列表（整体替换，缺省为空）")
    val tagIds: List<String>? = emptyList(),

    @Schema(description = "可见性（0 公开 / 1 私密，缺省公开）")
    val visibility: Int? = BlogPost.VISIBILITY_PUBLIC,

    @Schema(description = "是否置顶（缺省不置顶）")
    val isTop: Boolean? = false,

    @Schema(description = "是否允许评论（缺省允许）")
    val allowComment: Boolean? = true,

    @Schema(description = "SEO 标题")
    @field:Size(max = 200, message = "SEO标题不能超过200个字符")
    val seoTitle: String? = null,

    @Schema(description = "SEO 描述")
    @field:Size(max = 500, message = "SEO描述不能超过500个字符")
    val seoDescription: String? = null,
)

/** 管理端文章列表查询参数 */
@Schema(description = "管理端文章列表查询参数")
data class AdminPostQueryDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字（标题 / slug）")
    val search: String? = null,

    @Schema(description = "按状态过滤（0 草稿 / 1 已发布 / 2 已归档）")
    val status: Int? = null,

    @Schema(description = "按分类 ID 过滤")
    val categoryId: String? = null,

    @Schema(description = "按是否置顶过滤")
    val isTop: Boolean? = null,

    @Schema(description = "排序字段（createdAt / updatedAt / publishedAt / title）", defaultValue = "createdAt")
    val sortBy: String = "createdAt",

    @Schema(description = "排序方向（asc / desc）", defaultValue = "desc")
    val sortOrder: String = "desc",
)

/** 管理端文章列表项 */
@Schema(description = "管理端文章列表项")
data class PostAdminListItemDto(
    @Schema(description = "文章 ID") val id: String,
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "分类信息（未分类为 null）") val category: CategoryBriefDto?,
    @Schema(description = "标签列表") val tags: List<TagBriefDto>,
    @Schema(description = "状态（0 草稿 / 1 已发布 / 2 已归档）") val status: Int,
    @Schema(description = "可见性（0 公开 / 1 私密）") val visibility: Int,
    @get:JsonProperty("isTop")
    @Schema(description = "是否置顶") val isTop: Boolean,
    @Schema(description = "是否允许评论") val allowComment: Boolean,
    @Schema(description = "发布时间（未发布为 null）") val publishedAt: LocalDateTime?,
    @Schema(description = "累计阅读数") val viewCount: Long,
    @Schema(description = "当前有效点赞数") val likeCount: Long,
    @Schema(description = "已审核评论数") val commentCount: Long,
    @Schema(description = "创建时间") val createdAt: LocalDateTime,
    @Schema(description = "更新时间") val updatedAt: LocalDateTime,
)

/** 管理端文章列表响应 */
@Schema(description = "管理端文章列表响应")
data class PostAdminListResponseDto(
    @Schema(description = "文章列表") val list: List<PostAdminListItemDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)

/** 管理端文章详情（编辑回显数据，含全部字段与标签 ID） */
@Schema(description = "管理端文章详情")
data class PostDetailResponseDto(
    @Schema(description = "文章 ID") val id: String,
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "文章摘要") val summary: String?,
    @Schema(description = "Markdown 正文") val content: String,
    @Schema(description = "封面文件 ID") val coverFileId: String?,
    @Schema(description = "分类 ID（未分类为 null）") val categoryId: String?,
    @Schema(description = "标签 ID 列表") val tagIds: List<String>,
    @Schema(description = "状态（0 草稿 / 1 已发布 / 2 已归档）") val status: Int,
    @Schema(description = "可见性（0 公开 / 1 私密）") val visibility: Int,
    @get:JsonProperty("isTop")
    @Schema(description = "是否置顶") val isTop: Boolean,
    @Schema(description = "是否允许评论") val allowComment: Boolean,
    @Schema(description = "发布时间") val publishedAt: LocalDateTime?,
    @Schema(description = "SEO 标题") val seoTitle: String?,
    @Schema(description = "SEO 描述") val seoDescription: String?,
    @Schema(description = "创建时间") val createdAt: LocalDateTime,
    @Schema(description = "更新时间") val updatedAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 实体转管理端详情 DTO
         * @param post 文章实体
         * @param tagIds 标签 ID 列表
         * @return PostDetailResponseDto 管理端详情
         */
        fun from(post: BlogPost, tagIds: List<String>): PostDetailResponseDto = PostDetailResponseDto(
            id = post.id,
            title = post.title,
            slug = post.slug,
            summary = post.summary,
            content = post.content,
            coverFileId = post.coverFileId,
            categoryId = post.categoryId,
            tagIds = tagIds,
            status = post.status,
            visibility = post.visibility,
            isTop = post.isTop,
            allowComment = post.allowComment,
            publishedAt = post.publishedAt,
            seoTitle = post.seoTitle,
            seoDescription = post.seoDescription,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
        )
    }
}

/** 置顶设置请求 */
@Schema(description = "置顶设置请求")
data class SetPostTopDto(
    @Schema(description = "是否置顶", required = true)
    val isTop: Boolean,
)

/** 公开文章列表查询参数（经网关白名单匿名可访问） */
@Schema(description = "公开文章列表查询参数")
data class PublicPostQueryDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "按分类 slug 过滤")
    val categorySlug: String? = null,

    @Schema(description = "按标签 slug 过滤")
    val tagSlug: String? = null,

    @Schema(description = "按标题/摘要关键字搜索")
    val search: String? = null,
)

/** 公开文章列表项（不含正文） */
@Schema(description = "公开文章列表项")
data class PostPublicListItemDto(
    @Schema(description = "文章 ID") val id: String,
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "文章摘要") val summary: String?,
    @Schema(description = "封面文件 ID") val coverFileId: String?,
    @Schema(description = "分类信息（未分类为 null）") val category: CategoryBriefDto?,
    @Schema(description = "标签列表") val tags: List<TagBriefDto>,
    @get:JsonProperty("isTop")
    @Schema(description = "是否置顶") val isTop: Boolean,
    @Schema(description = "发布时间") val publishedAt: LocalDateTime?,
    @Schema(description = "累计阅读数") val viewCount: Long,
    @Schema(description = "当前有效点赞数") val likeCount: Long,
    @Schema(description = "已审核评论数") val commentCount: Long,
)

/** 公开文章列表响应 */
@Schema(description = "公开文章列表响应")
data class PostPublicListResponseDto(
    @Schema(description = "文章列表（置顶优先、发布时间倒序）") val list: List<PostPublicListItemDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)

/** 相邻文章简要信息（详情页上一篇/下一篇） */
@Schema(description = "相邻文章简要信息")
data class AdjacentPostDto(
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "文章标题") val title: String,
)

/** 公开文章详情（含正文、统计与上下篇） */
@Schema(description = "公开文章详情")
data class PostPublicDetailDto(
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "文章摘要") val summary: String?,
    @Schema(description = "Markdown 正文") val content: String,
    @Schema(description = "封面文件 ID") val coverFileId: String?,
    @Schema(description = "分类信息（未分类为 null）") val category: CategoryBriefDto?,
    @Schema(description = "标签列表") val tags: List<TagBriefDto>,
    @Schema(description = "发布时间") val publishedAt: LocalDateTime?,
    @Schema(description = "更新时间") val updatedAt: LocalDateTime,
    @Schema(description = "累计阅读数") val viewCount: Long,
    @Schema(description = "当前有效点赞数") val likeCount: Long,
    @Schema(description = "已审核评论数") val commentCount: Long,
    @Schema(description = "是否允许评论") val allowComment: Boolean,
    @Schema(description = "SEO 标题（空则前端回退文章标题）") val seoTitle: String?,
    @Schema(description = "SEO 描述（空则前端回退摘要）") val seoDescription: String?,
    @Schema(description = "上一篇（无则 null）") val prev: AdjacentPostDto?,
    @Schema(description = "下一篇（无则 null）") val next: AdjacentPostDto?,
)

/** 相关文章简要信息 */
@Schema(description = "相关文章简要信息")
data class RelatedPostDto(
    @Schema(description = "URL 标识") val slug: String,
    @Schema(description = "文章标题") val title: String,
    @Schema(description = "文章摘要") val summary: String?,
    @Schema(description = "发布时间") val publishedAt: LocalDateTime?,
)

/** 相关文章响应 */
@Schema(description = "相关文章响应")
data class RelatedPostsResponseDto(
    @Schema(description = "相关文章列表（最多 5 条）") val list: List<RelatedPostDto>,
)
