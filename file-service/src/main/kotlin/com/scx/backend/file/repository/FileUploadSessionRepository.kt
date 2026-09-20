package com.scx.backend.file.repository

import com.scx.backend.file.entity.FileUploadSession
import com.scx.backend.file.entity.UploadSessionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface FileUploadSessionRepository : JpaRepository<FileUploadSession, String> {

    /** 断点续传：按（用户, 哈希, 状态）找最近一条未完成会话 */
    fun findFirstByUserIdAndFileHashAndStatusOrderByCreatedAtDesc(
        userId: String,
        fileHash: String,
        status: UploadSessionStatus,
    ): FileUploadSession?

    /** 过期清理：扫描超时的上传中会话 */
    fun findByStatusAndExpiresAtBefore(status: UploadSessionStatus, time: LocalDateTime): List<FileUploadSession>
}
