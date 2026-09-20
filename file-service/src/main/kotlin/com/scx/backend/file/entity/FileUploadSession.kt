package com.scx.backend.file.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/** 上传会话状态 */
enum class UploadSessionStatus {
    /** 上传中（可续传） */
    UPLOADING,

    /** 已完成（fileId 已关联 files 表） */
    COMPLETED,

    /** 用户主动取消 */
    ABORTED,

    /** 超过过期时间被清理任务中止 */
    EXPIRED,
}

/**
 * 文件分片上传会话实体
 *
 * 一次大文件上传对应一条会话：init 时创建并关联 MinIO 侧 multipart uploadId，
 * 分片逐片落库 file_upload_parts，complete 后置 COMPLETED 并关联 files.id。
 * 会话按（userId, fileHash）匹配实现断点续传；expiresAt 随分片上传滑动续期。
 */
@Entity
@Table(name = "file_upload_sessions")
class FileUploadSession(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"userId\"", columnDefinition = "char(26)", nullable = false)
    var userId: String,

    @Column(name = "\"originalName\"", length = 255, nullable = false)
    var originalName: String,

    @Column(name = "\"mimeType\"", length = 100, nullable = false)
    var mimeType: String,

    @Column(name = "size", nullable = false)
    var size: Long,

    @Column(name = "\"chunkSize\"", nullable = false)
    var chunkSize: Int,

    @Column(name = "\"totalChunks\"", nullable = false)
    var totalChunks: Int,

    @Column(name = "\"fileHash\"", length = 64, nullable = false)
    var fileHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: UploadSessionStatus,

    @Column(name = "\"objectKey\"", length = 500, nullable = false)
    var objectKey: String,

    @Column(name = "\"minioUploadId\"", length = 255, nullable = false)
    var minioUploadId: String,

    @Column(name = "\"fileId\"", length = 30)
    var fileId: String? = null,

    @Column(name = "\"expiresAt\"", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
