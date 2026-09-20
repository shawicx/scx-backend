package com.scx.backend.file

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * @description 过期上传会话清理任务
 *
 * 定期扫描超过 expiresAt 仍处 UPLOADING 的分片上传会话：中止 MinIO 侧
 * multipart 上传（释放已传分片占用的存储）、会话置 EXPIRED、清理分片记录。
 * MinIO 中止失败的会话保持 UPLOADING，等待下一轮重试。
 */
@Component
class ChunkedUploadCleanupJob(
    private val chunkedUploadService: ChunkedUploadService,
) {

    private val logger = LoggerFactory.getLogger(ChunkedUploadCleanupJob::class.java)

    /**
     * @description 每小时执行一次过期会话清理
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    fun cleanup() {
        try {
            chunkedUploadService.cleanupExpiredSessions()
        } catch (ex: Exception) {
            logger.error("过期上传会话清理任务执行失败", ex)
        }
    }

    companion object {
        private const val CLEANUP_INTERVAL_MS = 60L * 60 * 1000
    }
}
