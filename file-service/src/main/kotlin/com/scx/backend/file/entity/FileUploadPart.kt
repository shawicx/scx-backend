package com.scx.backend.file.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime

/** 分片记录复合主键（会话 ID + 分片号） */
data class FileUploadPartKey(
    val uploadId: String = "",
    val partNumber: Int = 0,
) : Serializable

/**
 * 文件分片上传分片记录实体
 *
 * 记录每个已上传分片的 MinIO ETag 与大小：断点续传时据此返回已传分片号，
 * 完成时据此组装 CompleteMultipartUpload 的分片清单。同分片号重复上传时
 * 由复合主键 upsert 覆盖（后写胜）。
 */
@Entity
@Table(name = "file_upload_parts")
@IdClass(FileUploadPartKey::class)
class FileUploadPart(

    @Id
    @Column(name = "\"uploadId\"", nullable = false)
    var uploadId: String,

    @Id
    @Column(name = "\"partNumber\"", nullable = false)
    var partNumber: Int,

    @Column(name = "etag", length = 128, nullable = false)
    var etag: String,

    @Column(name = "size", nullable = false)
    var size: Long,

    @Column(name = "\"uploadedAt\"", nullable = false)
    var uploadedAt: LocalDateTime = LocalDateTime.now(),
)
