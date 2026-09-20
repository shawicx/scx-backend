package com.scx.backend.file.repository

import com.scx.backend.file.entity.FileUploadPart
import com.scx.backend.file.entity.FileUploadPartKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FileUploadPartRepository : JpaRepository<FileUploadPart, FileUploadPartKey> {

    /** 断点续传 / 完成校验：按分片号升序取已上传分片 */
    fun findByUploadIdOrderByPartNumberAsc(uploadId: String): List<FileUploadPart>

    /** 会话中止 / 过期清理：删除分片记录 */
    fun deleteByUploadId(uploadId: String)
}
