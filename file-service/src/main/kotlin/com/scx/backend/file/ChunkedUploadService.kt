package com.scx.backend.file

import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.file.dto.ChunkUploadResultDto
import com.scx.backend.file.dto.ChunkedUploadInitResponseDto
import com.scx.backend.file.dto.ChunkedUploadStatusDto
import com.scx.backend.file.dto.FileResponseDto
import com.scx.backend.file.dto.InitChunkedUploadDto
import com.scx.backend.file.entity.File
import com.scx.backend.file.entity.FileUploadPart
import com.scx.backend.file.entity.FileUploadSession
import com.scx.backend.file.entity.UploadSessionStatus
import com.scx.backend.file.repository.FileRepository
import com.scx.backend.file.repository.FileUploadPartRepository
import com.scx.backend.file.repository.FileUploadSessionRepository
import com.scx.backend.file.storage.MinioStorageService
import com.scx.backend.file.storage.PartEtag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.LocalDateTime

/**
 * @description 大文件分片上传服务
 *
 * 基于 MinIO 原生 S3 Multipart Upload：init 建会话 → 分片流式 UploadPart →
 * complete 服务端合并并落 files 表。支持断点续传（按 userId+fileHash 匹配
 * 未完成会话，分片记录驱动续传跳过）、秒传（同用户相同哈希直接复用文件）、
 * 主动取消与过期清理。会话严格按用户隔离（过程态不做管理员跨越）。
 */
@Service
class ChunkedUploadService(
    private val sessionRepository: FileUploadSessionRepository,
    private val partRepository: FileUploadPartRepository,
    private val fileRepository: FileRepository,
    private val storageService: MinioStorageService,
    @Value("\${file.upload.max-size:5368709120}") private val maxUploadSize: Long,
    @Value("\${file.upload.session-ttl-hours:24}") private val sessionTtlHours: Int,
) {

    private val logger = LoggerFactory.getLogger(ChunkedUploadService::class.java)

    /**
     * @description 初始化分片上传：秒传 → 续传复用 → 新建会话
     *
     * 分片大小与旧会话不一致时中止旧会话并新建（分片号无法对齐，旧分片不可复用）。
     * @param userId 归属用户 ID
     * @param dto 初始化请求（文件名 / 大小 / 分片大小 / SHA-256 哈希）
     * @returns ChunkedUploadInitResponseDto 秒传命中返回文件；否则返回会话 ID 与已传分片号
     */
    @Transactional
    fun initUpload(userId: String, dto: InitChunkedUploadDto): ChunkedUploadInitResponseDto {
        val fileName = dto.fileName?.trim().orEmpty()
        val mimeType = dto.mimeType?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "application/octet-stream"
        val fileHash = dto.fileHash?.trim().orEmpty()
        val size = dto.size
        val chunkSize = dto.chunkSize

        if (fileName.isEmpty()) {
            throw SystemException.invalidParameter("文件名不能为空")
        }
        if (fileName.length > 255) {
            throw SystemException.invalidParameter("文件名过长")
        }
        if (mimeType.length > 100) {
            throw SystemException.invalidParameter("MIME 类型过长")
        }
        if (size == null || size <= 0) {
            throw SystemException.invalidParameter("文件大小必须为正数")
        }
        if (size > maxUploadSize) {
            throw SystemException.invalidParameter("文件大小超出上限（${maxUploadSize} 字节）")
        }
        if (chunkSize == null || chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw SystemException.invalidParameter("分片大小必须在 ${MIN_CHUNK_SIZE}-${MAX_CHUNK_SIZE} 字节之间")
        }
        if (!FILE_HASH_PATTERN.matches(fileHash)) {
            throw SystemException.invalidParameter("文件哈希必须为 64 位十六进制（SHA-256）")
        }
        val normalizedHash = fileHash.lowercase()
        val totalChunks = ((size + chunkSize - 1) / chunkSize).toInt()

        // 秒传：同用户已有相同哈希的未删除文件，直接复用
        fileRepository.findFirstByUserIdAndFileHashAndDeletedAtIsNullOrderByCreatedAtDesc(userId, normalizedHash)
            ?.let { existing ->
                logger.info("秒传命中: userId={} fileHash={} fileId={}", userId, normalizedHash, existing.id)
                return ChunkedUploadInitResponseDto(
                    uploadId = null,
                    chunkSize = chunkSize,
                    totalChunks = totalChunks,
                    uploadedChunks = emptyList(),
                    instant = true,
                    file = FileResponseDto.from(existing, storageService.presignedGetUrl(existing.path)),
                )
            }

        // 续传：同用户同哈希存在上传中会话
        sessionRepository.findFirstByUserIdAndFileHashAndStatusOrderByCreatedAtDesc(
            userId,
            normalizedHash,
            UploadSessionStatus.UPLOADING,
        )?.let { session ->
            if (session.size == size && session.chunkSize == chunkSize) {
                val uploaded = partRepository.findByUploadIdOrderByPartNumberAsc(session.id)
                    .map { it.partNumber }
                logger.info("续传复用会话: userId={} sessionId={} 已传 {} 片", userId, session.id, uploaded.size)
                return ChunkedUploadInitResponseDto(
                    uploadId = session.id,
                    chunkSize = chunkSize,
                    totalChunks = session.totalChunks,
                    uploadedChunks = uploaded,
                    instant = false,
                    file = null,
                )
            }
            // 大小或分片规格不符：旧分片不可复用，中止旧会话后新建
            logger.info("会话规格不符，中止旧会话: sessionId={} 旧 chunkSize={} 新 chunkSize={}", session.id, session.chunkSize, chunkSize)
            abortSession(session)
        }

        // 新建会话：对象键此刻定死，避免 complete 时跨日期
        val objectKey = FileService.buildObjectKey(fileName)
        val minioUploadId = storageService.createMultipartUpload(objectKey, mimeType)
        val session = sessionRepository.save(
            FileUploadSession(
                id = IdGenerator.nextId(),
                userId = userId,
                originalName = fileName,
                mimeType = mimeType,
                size = size,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                fileHash = normalizedHash,
                status = UploadSessionStatus.UPLOADING,
                objectKey = objectKey,
                minioUploadId = minioUploadId,
                expiresAt = newExpiry(),
            ),
        )
        logger.info("分片上传会话已创建: sessionId={} userId={} size={} chunkSize={} totalChunks={}", session.id, userId, size, chunkSize, totalChunks)
        return ChunkedUploadInitResponseDto(
            uploadId = session.id,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            uploadedChunks = emptyList(),
            instant = false,
            file = null,
        )
    }

    /**
     * @description 查询上传会话进度（断点恢复时获取已传分片）
     * @param userId 当前用户 ID
     * @param uploadId 上传会话 ID
     * @returns ChunkedUploadStatusDto 会话状态与进度
     */
    fun getStatus(userId: String, uploadId: String): ChunkedUploadStatusDto {
        val session = loadOwnedSession(userId, uploadId)
        val parts = partRepository.findByUploadIdOrderByPartNumberAsc(uploadId)
        return ChunkedUploadStatusDto(
            uploadId = session.id,
            status = session.status.name,
            originalName = session.originalName,
            size = session.size,
            chunkSize = session.chunkSize,
            totalChunks = session.totalChunks,
            uploadedChunks = parts.map { it.partNumber },
            uploadedBytes = parts.sumOf { it.size },
            expiresAt = session.expiresAt,
        )
    }

    /**
     * @description 上传单个分片（流式直传 MinIO，严格校验分片大小，可选 MD5 比对）
     * @param userId 当前用户 ID
     * @param uploadId 上传会话 ID
     * @param partNumber 分片号（从 1 开始）
     * @param declaredSize 请求体声明的分片大小（Content-Length）
     * @param md5 客户端计算的分片 MD5（可选，与 MinIO ETag 比对）
     * @param data 分片内容流
     * @returns ChunkUploadResultDto 分片号与 ETag
     */
    @Transactional
    fun uploadChunk(
        userId: String,
        uploadId: String,
        partNumber: Int,
        declaredSize: Long,
        md5: String?,
        data: InputStream,
    ): ChunkUploadResultDto {
        val session = loadOwnedSession(userId, uploadId)
        requireUploading(session)
        requireNotExpired(session)
        if (partNumber !in 1..session.totalChunks) {
            throw SystemException.invalidParameter("分片号必须在 1-${session.totalChunks} 之间")
        }
        val expectedSize = expectedPartSize(session, partNumber)
        if (declaredSize != expectedSize) {
            throw SystemException.invalidParameter("分片大小不符：第 $partNumber 片应为 $expectedSize 字节，实际 $declaredSize 字节")
        }

        val etag = storageService.uploadPart(session.objectKey, session.minioUploadId, partNumber, declaredSize, data)
        if (md5 != null && !etag.equals(md5.trim().removeSurrounding("\""), ignoreCase = true)) {
            throw SystemException.invalidParameter("分片校验失败：MD5 与服务端 ETag 不一致")
        }
        partRepository.save(FileUploadPart(uploadId, partNumber, etag, declaredSize))

        // 活跃会话滑动续期
        session.expiresAt = newExpiry()
        session.updatedAt = LocalDateTime.now()
        sessionRepository.save(session)
        return ChunkUploadResultDto(partNumber = partNumber, etag = etag, size = declaredSize)
    }

    /**
     * @description 完成分片上传：校验分片齐全 → MinIO 服务端合并 → 落 files 表
     *
     * 幂等：会话已完成时直接返回关联文件（客户端重试安全）。
     * @param userId 当前用户 ID
     * @param uploadId 上传会话 ID
     * @returns FileResponseDto 文件信息（url 为预签名直链）
     */
    @Transactional
    fun completeUpload(userId: String, uploadId: String): FileResponseDto {
        val session = loadOwnedSession(userId, uploadId)
        if (session.status == UploadSessionStatus.COMPLETED) {
            val fileId = session.fileId
                ?: throw SystemException.operationFailed("上传会话状态异常，请重新上传")
            val file = fileRepository.findById(fileId)
                .orElseThrow { SystemException.dataNotFound("文件不存在或已删除") }
            return FileResponseDto.from(file, storageService.presignedGetUrl(file.path))
        }
        requireUploading(session)

        val parts = partRepository.findByUploadIdOrderByPartNumberAsc(uploadId)
        if (parts.size != session.totalChunks || parts.sumOf { it.size } != session.size) {
            throw SystemException.operationFailed("分片不完整：已传 ${parts.size}/${session.totalChunks} 片，请补齐后重试")
        }
        storageService.completeMultipartUpload(
            session.objectKey,
            session.minioUploadId,
            parts.map { PartEtag(it.partNumber, it.etag) },
        )

        val file = fileRepository.save(
            File(
                id = IdGenerator.nextId(),
                userId = session.userId,
                originalName = session.originalName,
                mimeType = session.mimeType,
                size = session.size,
                path = session.objectKey,
                url = storageService.logicalUrl(session.objectKey),
                fileHash = session.fileHash,
            ),
        )
        session.status = UploadSessionStatus.COMPLETED
        session.fileId = file.id
        session.updatedAt = LocalDateTime.now()
        sessionRepository.save(session)
        logger.info("分片上传完成: sessionId={} userId={} fileId={} size={}", session.id, userId, file.id, file.size)
        return FileResponseDto.from(file, storageService.presignedGetUrl(session.objectKey))
    }

    /**
     * @description 取消上传会话（中止 MinIO 侧上传并清理分片记录，幂等）
     * @param userId 当前用户 ID
     * @param uploadId 上传会话 ID
     */
    @Transactional
    fun abortUpload(userId: String, uploadId: String) {
        val session = loadOwnedSession(userId, uploadId)
        if (session.status == UploadSessionStatus.ABORTED) return
        if (session.status != UploadSessionStatus.UPLOADING) {
            throw SystemException.operationFailed("已结束的上传会话不可取消")
        }
        abortSession(session)
    }

    /**
     * @description 清理过期会话：中止 MinIO 侧上传并置 EXPIRED
     *
     * MinIO 中止失败的会话保持 UPLOADING，等待下一轮清理重试（避免存储泄漏）。
     * @returns Int 本轮成功清理的会话数
     */
    @Transactional
    fun cleanupExpiredSessions(): Int {
        val expired = sessionRepository.findByStatusAndExpiresAtBefore(
            UploadSessionStatus.UPLOADING,
            LocalDateTime.now(),
        )
        var cleaned = 0
        for (session in expired) {
            try {
                storageService.abortMultipartUpload(session.objectKey, session.minioUploadId)
            } catch (ex: SystemException) {
                logger.warn("过期会话中止失败，留待下轮清理: sessionId={}", session.id, ex)
                continue
            }
            session.status = UploadSessionStatus.EXPIRED
            session.updatedAt = LocalDateTime.now()
            sessionRepository.save(session)
            partRepository.deleteByUploadId(session.id)
            cleaned++
        }
        if (cleaned > 0) {
            logger.info("过期上传会话清理完成: 本轮 {} 个（扫描 {} 个）", cleaned, expired.size)
        }
        return cleaned
    }

    /**
     * @description 中止会话的内部路径（取消 / 规格不符重建 / 均复用）
     * @param session 上传会话实体
     */
    private fun abortSession(session: FileUploadSession) {
        storageService.abortMultipartUpload(session.objectKey, session.minioUploadId)
        session.status = UploadSessionStatus.ABORTED
        session.updatedAt = LocalDateTime.now()
        sessionRepository.save(session)
        partRepository.deleteByUploadId(session.id)
    }

    /**
     * @description 加载会话并校验归属（数据不存在 / 越权）
     * @param userId 当前用户 ID
     * @param uploadId 上传会话 ID
     * @returns FileUploadSession 会话实体
     */
    private fun loadOwnedSession(userId: String, uploadId: String): FileUploadSession {
        val session = sessionRepository.findById(uploadId)
            .orElseThrow { SystemException.dataNotFound("上传会话不存在") }
        if (session.userId != userId) {
            throw SystemException.insufficientPermission("无权访问该上传会话")
        }
        return session
    }

    /**
     * @description 校验会话处于上传中状态
     * @param session 会话实体
     */
    private fun requireUploading(session: FileUploadSession) {
        if (session.status != UploadSessionStatus.UPLOADING) {
            throw SystemException.operationFailed("上传会话已结束，请重新发起上传")
        }
    }

    /**
     * @description 校验会话未过期（过期会话的 MinIO 侧凭据即将被清理任务回收）
     * @param session 会话实体
     */
    private fun requireNotExpired(session: FileUploadSession) {
        if (session.expiresAt.isBefore(LocalDateTime.now())) {
            throw SystemException.invalidParameter("上传会话已过期，请重新发起上传")
        }
    }

    /**
     * @description 计算指定分片的期望大小（末片为余量）
     * @param session 会话实体
     * @param partNumber 分片号
     * @returns Long 期望分片大小（字节）
     */
    private fun expectedPartSize(session: FileUploadSession, partNumber: Int): Long =
        if (partNumber == session.totalChunks) {
            session.size - (session.totalChunks - 1L) * session.chunkSize
        } else {
            session.chunkSize.toLong()
        }

    /**
     * @description 计算会话新的过期时间（当前时间 + TTL）
     * @returns LocalDateTime 新过期时间
     */
    private fun newExpiry(): LocalDateTime = LocalDateTime.now().plusHours(sessionTtlHours.toLong())

    companion object {
        /** SHA-256 十六进制（64 位，大小写均可） */
        private val FILE_HASH_PATTERN = Regex("^[0-9a-fA-F]{64}$")

        /** 分片大小下限：S3 规范非末片最小 5MB（MinIO 合并时校验 EntityTooSmall） */
        const val MIN_CHUNK_SIZE = 5L * 1024 * 1024

        /** 分片大小上限 */
        const val MAX_CHUNK_SIZE = 64L * 1024 * 1024
    }
}
