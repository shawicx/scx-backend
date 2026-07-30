package com.scx.backend.modules.file.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 文件查询参数 */
@Schema(description = "文件查询参数")
data class QueryFilesDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字（按原始文件名）")
    val search: String? = null,

    @Schema(description = "按 MIME 类型过滤")
    val mimeType: String? = null,

    @Schema(description = "排序字段", defaultValue = "createdAt")
    val sortBy: String = "createdAt",

    @Schema(description = "排序方向：ASC / DESC", defaultValue = "DESC")
    val sortOrder: String = "DESC",
)

/** 批量删除文件请求 */
@Schema(description = "批量删除文件请求")
data class DeleteFilesDto(
    @Schema(description = "文件 ID 列表", required = true)
    @field:NotEmpty(message = "至少选择一个文件")
    val ids: List<String>,
)

/** 文件信息响应 */
@Schema(description = "文件信息响应")
data class FileResponseDto(
    @Schema(description = "文件 ID")
    val id: String,

    @Schema(description = "所属用户 ID")
    val userId: String,

    @Schema(description = "原始文件名")
    val originalName: String,

    @Schema(description = "MIME 类型")
    val mimeType: String,

    @Schema(description = "文件大小（字节）")
    val size: Int,

    @Schema(description = "存储路径")
    val path: String,

    @Schema(description = "访问 URL")
    val url: String,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,

    @Schema(description = "删除时间（逻辑删除）")
    val deletedAt: LocalDateTime?,
)

/** 文件列表响应 */
@Schema(description = "文件列表响应")
data class FileListResponseDto(
    @Schema(description = "文件列表")
    val list: List<FileResponseDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)
