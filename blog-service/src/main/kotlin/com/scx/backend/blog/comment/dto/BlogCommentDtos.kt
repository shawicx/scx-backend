package com.scx.backend.blog.comment.dto

import com.scx.backend.blog.entity.BlogComment
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * @description 博客评论相关 DTO（匿名提交 / 公开已审列表 / 管理端审核）
 */

/** 匿名发表评论请求（无需登录；默认待审核） */
@Schema(description = "匿名发表评论请求")
data class CreateCommentDto(
    @Schema(description = "访客昵称", required = true)
    @field:NotBlank(message = "昵称不能为空")
    @field:Size(max = 60, message = "昵称不能超过60个字符")
    val nickname: String,

    @Schema(description = "邮箱（选填，不公开）")
    @field:Email(message = "邮箱格式不正确")
    @field:Size(max = 254, message = "邮箱不能超过254个字符")
    val email: String? = null,

    @Schema(description = "个人网站（选填）")
    @field:Size(max = 255, message = "个人网站不能超过255个字符")
    val website: String? = null,

    @Schema(description = "评论正文（纯文本）", required = true)
    @field:NotBlank(message = "评论内容不能为空")
    @field:Size(max = 2000, message = "评论内容不能超过2000个字符")
    val content: String,

    @Schema(description = "回复的评论 ID（仅支持回复一级评论，选填）")
    @field:Size(max = 30, message = "评论ID不能超过30个字符")
    val parentId: String? = null,
)

/** 公开评论查询参数 */
@Schema(description = "公开评论查询参数")
data class PublicCommentQueryDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "20")
    val limit: Int = 20,
)

/** 公开评论响应（不含邮箱/IP 等私有字段） */
@Schema(description = "公开评论响应")
data class CommentPublicResponseDto(
    @Schema(description = "评论 ID") val id: String,
    @Schema(description = "访客昵称") val nickname: String,
    @Schema(description = "个人网站") val website: String?,
    @Schema(description = "评论正文（纯文本，前端负责转义）") val content: String,
    @Schema(description = "直接回复的评论 ID（一级评论为 null）") val parentId: String?,
    @Schema(description = "所属根评论 ID（一级评论为 null）") val rootId: String?,
    @Schema(description = "发布时间") val createdAt: LocalDateTime,
)

/** 公开评论列表响应 */
@Schema(description = "公开评论列表响应")
data class CommentPublicListResponseDto(
    @Schema(description = "已审核评论列表（时间正序）") val list: List<CommentPublicResponseDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
)

/** 管理端评论列表查询参数 */
@Schema(description = "管理端评论列表查询参数")
data class AdminCommentQueryDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "按状态过滤（0 待审核 / 1 已通过 / 2 已拒绝）")
    val status: Int? = null,

    @Schema(description = "按文章 ID 过滤")
    val postId: String? = null,

    @Schema(description = "搜索关键字（昵称 / 评论内容）")
    val search: String? = null,
)

/** 管理端评论列表项（含完整内容与私有字段） */
@Schema(description = "管理端评论列表项")
data class CommentAdminListItemDto(
    @Schema(description = "评论 ID") val id: String,
    @Schema(description = "所属文章 ID") val postId: String,
    @Schema(description = "所属文章标题") val postTitle: String,
    @Schema(description = "访客昵称") val nickname: String,
    @Schema(description = "邮箱（仅管理端可见）") val email: String?,
    @Schema(description = "个人网站") val website: String?,
    @Schema(description = "评论正文") val content: String,
    @Schema(description = "直接回复的评论 ID") val parentId: String?,
    @Schema(description = "状态（0 待审核 / 1 已通过 / 2 已拒绝）") val status: Int,
    @Schema(description = "提交时间") val createdAt: LocalDateTime,
)

/** 管理端评论列表响应 */
@Schema(description = "管理端评论列表响应")
data class CommentAdminListResponseDto(
    @Schema(description = "评论列表（提交时间倒序）") val list: List<CommentAdminListItemDto>,
    @Schema(description = "总数") val total: Long,
    @Schema(description = "页码") val page: Int,
    @Schema(description = "每页条数") val limit: Int,
) {
    companion object {
        /**
         * @description 实体 + 文章标题组装管理端列表项
         * @param comment 评论实体
         * @param postTitle 所属文章标题
         * @return CommentAdminListItemDto 管理端列表项
         */
        fun itemOf(comment: BlogComment, postTitle: String): CommentAdminListItemDto =
            CommentAdminListItemDto(
                id = comment.id,
                postId = comment.postId,
                postTitle = postTitle,
                nickname = comment.nickname,
                email = comment.email,
                website = comment.website,
                content = comment.content,
                parentId = comment.parentId,
                status = comment.status,
                createdAt = comment.createdAt,
            )
    }
}
