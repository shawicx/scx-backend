package com.scx.backend.file.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 分片上传初始化请求
 *
 * 字段可为空 + 服务端校验（Jackson 3 对缺失字段落 null，业务规则统一在
 * ChunkedUploadService 校验，保证直连调用与 HTTP 调用行为一致）。
 */
@Schema(description = "分片上传初始化请求")
data class InitChunkedUploadDto(
    @Schema(description = "原始文件名", required = true)
    @field:NotBlank(message = "文件名不能为空")
    @field:Size(max = 255, message = "文件名过长")
    val fileName: String? = null,

    @Schema(description = "MIME 类型，缺省按二进制流处理")
    @field:Size(max = 100, message = "MIME 类型过长")
    val mimeType: String? = null,

    @Schema(description = "文件总大小（字节）", required = true)
    val size: Long? = null,

    @Schema(description = "分片大小（字节），范围 5MB-64MB", required = true)
    val chunkSize: Int? = null,

    @Schema(description = "文件 SHA-256 哈希（64 位十六进制），用于断点续传与秒传", required = true)
    @field:NotBlank(message = "文件哈希不能为空")
    val fileHash: String? = null,
)

/** 分片上传初始化响应 */
@Schema(description = "分片上传初始化响应")
data class ChunkedUploadInitResponseDto(
    @Schema(description = "上传会话 ID；秒传命中时为空")
    val uploadId: String?,

    @Schema(description = "分片大小（字节）")
    val chunkSize: Int,

    @Schema(description = "总分片数")
    val totalChunks: Int,

    @Schema(description = "已上传分片号列表（断点续传时跳过这些分片）")
    val uploadedChunks: List<Int>,

    @Schema(description = "是否秒传命中（同用户已存在相同哈希的文件）")
    val instant: Boolean,

    @Schema(description = "秒传命中时的文件信息")
    val file: FileResponseDto?,
)

/** 上传会话进度响应 */
@Schema(description = "上传会话进度响应")
data class ChunkedUploadStatusDto(
    @Schema(description = "上传会话 ID")
    val uploadId: String,

    @Schema(description = "会话状态：UPLOADING / COMPLETED / ABORTED / EXPIRED")
    val status: String,

    @Schema(description = "原始文件名")
    val originalName: String,

    @Schema(description = "文件总大小（字节）")
    val size: Long,

    @Schema(description = "分片大小（字节）")
    val chunkSize: Int,

    @Schema(description = "总分片数")
    val totalChunks: Int,

    @Schema(description = "已上传分片号列表")
    val uploadedChunks: List<Int>,

    @Schema(description = "已上传字节数（进度统计）")
    val uploadedBytes: Long,

    @Schema(description = "会话过期时间")
    val expiresAt: LocalDateTime,
)

/** 分片上传结果响应 */
@Schema(description = "分片上传结果响应")
data class ChunkUploadResultDto(
    @Schema(description = "分片号")
    val partNumber: Int,

    @Schema(description = "分片 ETag（内容 MD5，可用于客户端校验）")
    val etag: String,

    @Schema(description = "分片大小（字节）")
    val size: Long,
)
