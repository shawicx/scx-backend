package com.scx.backend.modules.file.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 文件查询（对标 QueryFilesDto） */
data class QueryFilesDto(
    val page: Int = 1,
    val limit: Int = 10,
    val search: String? = null,
    val mimeType: String? = null,
    val sortBy: String = "createdAt",
    val sortOrder: String = "DESC",
)

/** 批量删除（对标 DeleteFilesDto） */
data class DeleteFilesDto(
    @field:NotEmpty(message = "至少选择一个文件")
    val ids: List<String>,
)

/** 文件响应（对标 FileResponseDto） */
data class FileResponseDto(
    val id: String,
    val userId: String,
    val originalName: String,
    val mimeType: String,
    val size: Int,
    val path: String,
    val url: String,
    val createdAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

/** 文件列表响应（对标 FileListResponseDto） */
data class FileListResponseDto(
    val list: List<FileResponseDto>,
    val total: Long,
    val page: Int,
    val limit: Int,
)
