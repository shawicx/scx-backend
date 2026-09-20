package com.scx.backend.file

import com.scx.backend.common.exception.SystemErrorCode
import com.scx.backend.common.exception.SystemException
import com.scx.backend.file.dto.ChunkUploadResultDto
import com.scx.backend.file.dto.ChunkedUploadInitResponseDto
import com.scx.backend.file.dto.ChunkedUploadStatusDto
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.Optional

/**
 * @description 分片上传服务单元测试
 *
 * 纯 Mockito 单元测试（不依赖真实 MinIO 与数据库）：
 * 覆盖 init 三分支（新建/秒传/续传）、参数校验、分片上传的状态与大小与 MD5 校验、
 * 完成的分片完整性校验与幂等、取消的幂等、过期会话清理。
 */
class ChunkedUploadServiceTest {

    private val sessionRepository = mock(FileUploadSessionRepository::class.java)
    private val partRepository = mock(FileUploadPartRepository::class.java)
    private val fileRepository = mock(FileRepository::class.java)
    private val storageService = mock(MinioStorageService::class.java)
    private val service = ChunkedUploadService(
        sessionRepository = sessionRepository,
        partRepository = partRepository,
        fileRepository = fileRepository,
        storageService = storageService,
        maxUploadSize = 5L * 1024 * 1024 * 1024,
        sessionTtlHours = 24,
    )

    /** 分片大小：5MB（S3 规范非末片最小值） */
    private val chunkSize = 5 * 1024 * 1024

    /** 文件总大小：12MB → 3 片（5MB + 5MB + 2MB） */
    private val totalSize = 12L * 1024 * 1024

    // ---------- init：参数校验 ----------

    @Test
    fun `initUpload rejects file exceeding max size`() {
        val ex = assertThrows<SystemException> {
            service.initUpload("user-1", initDto(size = 5L * 1024 * 1024 * 1024 + 1))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
        verifyNoInteractions(storageService)
    }

    @Test
    fun `initUpload rejects chunk size below minimum`() {
        val ex = assertThrows<SystemException> {
            service.initUpload("user-1", initDto(chunkSize = 1024 * 1024))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    @Test
    fun `initUpload rejects chunk size above maximum`() {
        val ex = assertThrows<SystemException> {
            service.initUpload("user-1", initDto(chunkSize = 65 * 1024 * 1024))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    @Test
    fun `initUpload rejects malformed file hash`() {
        val ex = assertThrows<SystemException> {
            service.initUpload("user-1", initDto(fileHash = "not-a-sha256"))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    @Test
    fun `initUpload rejects blank file name`() {
        val ex = assertThrows<SystemException> {
            service.initUpload("user-1", initDto(fileName = " "))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    // ---------- init：秒传 ----------

    @Test
    fun `initUpload returns existing file when hash already uploaded`() {
        val existing = fileEntity(id = "f-existing")
        given(
            fileRepository.findFirstByUserIdAndFileHashAndDeletedAtIsNullOrderByCreatedAtDesc(
                anyString(),
                anyString(),
            ),
        ).willReturn(existing)
        given(storageService.presignedGetUrl(existing.path)).willReturn("http://presigned")

        val result = service.initUpload("user-1", initDto())

        assertTrue(result.instant, "同哈希已上传过应秒传命中")
        assertNull(result.uploadId, "秒传时不应返回会话 ID")
        assertEquals("f-existing", result.file?.id)
        assertEquals("http://presigned", result.file?.url)
        verify(storageService, never()).createMultipartUpload(anyString(), anyString())
    }

    // ---------- init：续传 ----------

    @Test
    fun `initUpload reuses uploading session and returns uploaded chunks`() {
        val session = sessionEntity()
        given(
            sessionRepository.findFirstByUserIdAndFileHashAndStatusOrderByCreatedAtDesc(
                anyString(),
                anyString(),
                anyStatus(),
            ),
        ).willReturn(session)
        given(partRepository.findByUploadIdOrderByPartNumberAsc(session.id))
            .willReturn(listOf(partEntity(session.id, 1), partEntity(session.id, 2)))

        val result = service.initUpload("user-1", initDto())

        assertEquals(session.id, result.uploadId, "应复用未完成会话")
        assertEquals(listOf(1, 2), result.uploadedChunks, "应返回已上传分片号")
        verify(storageService, never()).createMultipartUpload(anyString(), anyString())
    }

    @Test
    fun `initUpload aborts stale session when chunk size differs`() {
        val session = sessionEntity(chunkSize = 6 * 1024 * 1024)
        given(
            sessionRepository.findFirstByUserIdAndFileHashAndStatusOrderByCreatedAtDesc(
                anyString(),
                anyString(),
                anyStatus(),
            ),
        ).willReturn(session)
        given(storageService.createMultipartUpload(anyString(), anyString())).willReturn("minio-upload-2")
        given(sessionRepository.save(any<FileUploadSession>())).willAnswer { it.arguments[0] }

        val result = service.initUpload("user-1", initDto())

        verify(storageService).abortMultipartUpload(session.objectKey, session.minioUploadId)
        verify(storageService).createMultipartUpload(anyString(), anyString())
        assertNotNull(result.uploadId, "分片大小不符时应中止旧会话并新建")
        assertTrue(result.uploadId != session.id, "新建会话 ID 不应复用旧会话")
        assertEquals(UploadSessionStatus.ABORTED, session.status)
    }

    // ---------- init：新建 ----------

    @Test
    fun `initUpload creates session with computed total chunks`() {
        var savedSession: FileUploadSession? = null
        given(
            sessionRepository.findFirstByUserIdAndFileHashAndStatusOrderByCreatedAtDesc(
                anyString(),
                anyString(),
                anyStatus(),
            ),
        ).willReturn(null)
        given(storageService.createMultipartUpload(anyString(), anyString())).willReturn("minio-upload-1")
        given(sessionRepository.save(any<FileUploadSession>())).willAnswer {
            savedSession = it.arguments[0] as FileUploadSession
            it.arguments[0]
        }

        val result = service.initUpload("user-1", initDto())

        verify(storageService).createMultipartUpload(anyString(), anyString())
        assertEquals(3, result.totalChunks, "12MB / 5MB 应切为 3 片")
        assertTrue(result.uploadedChunks.isEmpty(), "新会话不应有已上传分片")
        assertNotNull(result.uploadId)
        assertEquals(3, savedSession?.totalChunks)
        assertEquals(UploadSessionStatus.UPLOADING, savedSession?.status)
    }

    // ---------- 分片上传：校验 ----------

    @Test
    fun `uploadChunk rejects mismatched part size`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity()))

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 2, declaredSize = 4L * 1024 * 1024, md5 = null, data = emptyStream())
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), anyLong(), anyInputStream())
    }

    @Test
    fun `uploadChunk rejects out-of-range part number`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity()))

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 4, declaredSize = chunkSize.toLong(), md5 = null, data = emptyStream())
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    @Test
    fun `uploadChunk rejects expired session`() {
        given(sessionRepository.findById("sess-1")).willReturn(
            Optional.of(sessionEntity(expiresAt = LocalDateTime.now().minusHours(1))),
        )

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 1, declaredSize = chunkSize.toLong(), md5 = null, data = emptyStream())
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    @Test
    fun `uploadChunk rejects finished session`() {
        given(sessionRepository.findById("sess-1")).willReturn(
            Optional.of(sessionEntity(status = UploadSessionStatus.COMPLETED)),
        )

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 1, declaredSize = chunkSize.toLong(), md5 = null, data = emptyStream())
        }
        assertEquals(SystemErrorCode.OPERATION_FAILED.code, ex.code)
    }

    @Test
    fun `uploadChunk rejects other users session`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity(userId = "owner")))

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 1, declaredSize = chunkSize.toLong(), md5 = null, data = emptyStream())
        }
        assertEquals(SystemErrorCode.INSUFFICIENT_PERMISSION.code, ex.code)
    }

    @Test
    fun `uploadChunk rejects md5 mismatch`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity()))
        given(storageService.uploadPart(anyString(), anyString(), anyInt(), anyLong(), anyInputStream()))
            .willReturn("abc123")

        val ex = assertThrows<SystemException> {
            service.uploadChunk("user-1", "sess-1", 1, chunkSize.toLong(), md5 = "deadbeef", data = emptyStream())
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
    }

    // ---------- 分片上传：成功路径 ----------

    @Test
    fun `uploadChunk stores part with case-insensitive md5 and slides expiry`() {
        val session = sessionEntity(expiresAt = LocalDateTime.now().plusHours(1))
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(session))
        given(storageService.uploadPart(anyString(), anyString(), anyInt(), anyLong(), anyInputStream()))
            .willReturn("abc123")

        val result = service.uploadChunk(
            "user-1", "sess-1", 3,
            declaredSize = 2L * 1024 * 1024, md5 = "ABC123", data = emptyStream(),
        )

        assertEquals(3, result.partNumber)
        assertEquals("abc123", result.etag)
        assertEquals(2L * 1024 * 1024, result.size)
        verify(partRepository).save(any<FileUploadPart>())
        assertTrue(session.expiresAt.isAfter(LocalDateTime.now().plusHours(1)), "分片上传应滑动续期会话")
    }

    // ---------- 状态查询 ----------

    @Test
    fun `getStatus returns progress with uploaded bytes`() {
        val session = sessionEntity()
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(session))
        given(partRepository.findByUploadIdOrderByPartNumberAsc("sess-1")).willReturn(
            listOf(partEntity("sess-1", 1, size = chunkSize.toLong()), partEntity("sess-1", 2, size = chunkSize.toLong())),
        )

        val status: ChunkedUploadStatusDto = service.getStatus("user-1", "sess-1")

        assertEquals("sess-1", status.uploadId)
        assertEquals(listOf(1, 2), status.uploadedChunks)
        assertEquals(10L * 1024 * 1024, status.uploadedBytes)
        assertEquals(3, status.totalChunks)
    }

    // ---------- 完成 ----------

    @Test
    fun `completeUpload rejects incomplete parts`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity()))
        given(partRepository.findByUploadIdOrderByPartNumberAsc("sess-1")).willReturn(
            listOf(partEntity("sess-1", 1), partEntity("sess-1", 2)),
        )

        val ex = assertThrows<SystemException> { service.completeUpload("user-1", "sess-1") }
        assertEquals(SystemErrorCode.OPERATION_FAILED.code, ex.code)
        verify(storageService, never()).completeMultipartUpload(anyString(), anyString(), anyParts())
    }

    @Test
    fun `completeUpload rejects size sum mismatch`() {
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(sessionEntity()))
        given(partRepository.findByUploadIdOrderByPartNumberAsc("sess-1")).willReturn(
            listOf(
                partEntity("sess-1", 1),
                partEntity("sess-1", 2),
                partEntity("sess-1", 3, size = 3L * 1024 * 1024),
            ),
        )

        val ex = assertThrows<SystemException> { service.completeUpload("user-1", "sess-1") }
        assertEquals(SystemErrorCode.OPERATION_FAILED.code, ex.code)
    }

    @Test
    fun `completeUpload merges parts and persists file`() {
        val session = sessionEntity()
        var mergedParts: List<PartEtag>? = null
        var savedFile: File? = null
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(session))
        given(partRepository.findByUploadIdOrderByPartNumberAsc("sess-1")).willReturn(
            listOf(
                partEntity("sess-1", 1, etag = "etag-1"),
                partEntity("sess-1", 2, etag = "etag-2"),
                partEntity("sess-1", 3, etag = "etag-3", size = 2L * 1024 * 1024),
            ),
        )
        given(storageService.completeMultipartUpload(anyString(), anyString(), anyParts())).willAnswer {
            mergedParts = it.arguments[2] as List<PartEtag>
            null
        }
        given(storageService.logicalUrl(anyString())).willReturn("http://minio:9000/scx-files/uploads/a.mp4")
        given(storageService.presignedGetUrl(anyString())).willReturn("http://presigned")
        given(fileRepository.save(any<File>())).willAnswer {
            savedFile = it.arguments[0] as File
            it.arguments[0]
        }

        val dto = service.completeUpload("user-1", "sess-1")

        assertEquals(3, mergedParts?.size)
        assertEquals(1, mergedParts?.first()?.partNumber)
        assertEquals("etag-1", mergedParts?.first()?.etag)
        assertEquals("http://presigned", dto.url)
        assertEquals(session.objectKey, dto.path)
        assertEquals(totalSize, dto.size)
        assertEquals(UploadSessionStatus.COMPLETED, session.status)
        assertNotNull(session.fileId, "完成后应关联文件 ID")
        assertEquals(session.fileHash, savedFile?.fileHash)
        assertEquals(session.objectKey, savedFile?.path)
    }

    @Test
    fun `completeUpload is idempotent after completion`() {
        val session = sessionEntity(status = UploadSessionStatus.COMPLETED, fileId = "f-done")
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(session))
        given(fileRepository.findById("f-done")).willReturn(Optional.of(fileEntity(id = "f-done")))
        given(storageService.presignedGetUrl(anyString())).willReturn("http://presigned")

        val dto = service.completeUpload("user-1", "sess-1")

        assertEquals("f-done", dto.id)
        verify(storageService, never()).completeMultipartUpload(anyString(), anyString(), anyParts())
    }

    // ---------- 取消 ----------

    @Test
    fun `abortUpload aborts minio upload and marks session`() {
        val session = sessionEntity()
        given(sessionRepository.findById("sess-1")).willReturn(Optional.of(session))

        service.abortUpload("user-1", "sess-1")

        verify(storageService).abortMultipartUpload(session.objectKey, session.minioUploadId)
        assertEquals(UploadSessionStatus.ABORTED, session.status)
        verify(partRepository).deleteByUploadId("sess-1")
    }

    @Test
    fun `abortUpload is idempotent for aborted session`() {
        given(sessionRepository.findById("sess-1")).willReturn(
            Optional.of(sessionEntity(status = UploadSessionStatus.ABORTED)),
        )

        service.abortUpload("user-1", "sess-1")

        verifyNoInteractions(storageService)
    }

    @Test
    fun `abortUpload rejects completed session`() {
        given(sessionRepository.findById("sess-1")).willReturn(
            Optional.of(sessionEntity(status = UploadSessionStatus.COMPLETED, fileId = "f-done")),
        )

        val ex = assertThrows<SystemException> { service.abortUpload("user-1", "sess-1") }
        assertEquals(SystemErrorCode.OPERATION_FAILED.code, ex.code)
    }

    // ---------- 过期清理 ----------

    @Test
    fun `cleanupExpiredSessions aborts expired and keeps failed ones uploading`() {
        val okSession = sessionEntity(id = "sess-ok")
        val failSession = sessionEntity(id = "sess-fail")
        given(sessionRepository.findByStatusAndExpiresAtBefore(anyStatus(), anyTime()))
            .willReturn(listOf(okSession, failSession))
        given(storageService.abortMultipartUpload(failSession.objectKey, failSession.minioUploadId))
            .willThrow(SystemException.serviceUnavailable())

        val count = service.cleanupExpiredSessions()

        assertEquals(1, count, "仅成功中止的会话计入清理数")
        assertEquals(UploadSessionStatus.EXPIRED, okSession.status)
        assertEquals(UploadSessionStatus.UPLOADING, failSession.status, "中止失败的会话应保持 UPLOADING 等待下轮重试")
        verify(partRepository).deleteByUploadId("sess-ok")
        verify(partRepository, never()).deleteByUploadId("sess-fail")
    }

    // ---------- 辅助构造 ----------

    /**
     * @description 构造初始化请求 DTO
     * @param fileName 文件名
     * @param size 文件总大小（字节）
     * @param chunkSize 分片大小（字节）
     * @param fileHash 文件 SHA-256 哈希
     * @returns InitChunkedUploadDto 初始化请求
     */
    private fun initDto(
        fileName: String = "movie.mp4",
        size: Long = totalSize,
        chunkSize: Int = this.chunkSize,
        fileHash: String = "a".repeat(64),
    ): InitChunkedUploadDto = InitChunkedUploadDto(
        fileName = fileName,
        mimeType = "video/mp4",
        size = size,
        chunkSize = chunkSize,
        fileHash = fileHash,
    )

    /**
     * @description 构造上传会话实体
     * @returns FileUploadSession 上传会话实体
     */
    private fun sessionEntity(
        id: String = "sess-1",
        userId: String = "user-1",
        status: UploadSessionStatus = UploadSessionStatus.UPLOADING,
        size: Long = totalSize,
        chunkSize: Int = this.chunkSize,
        fileHash: String = "a".repeat(64),
        expiresAt: LocalDateTime = LocalDateTime.now().plusHours(23),
        fileId: String? = null,
    ): FileUploadSession = FileUploadSession(
        id = id,
        userId = userId,
        originalName = "movie.mp4",
        mimeType = "video/mp4",
        size = size,
        chunkSize = chunkSize,
        totalChunks = 3,
        fileHash = fileHash,
        status = status,
        objectKey = "uploads/2026/09/20/$id.mp4",
        minioUploadId = "minio-upload-$id",
        fileId = fileId,
        expiresAt = expiresAt,
    )

    /**
     * @description 构造分片记录实体
     * @returns FileUploadPart 分片记录实体
     */
    private fun partEntity(
        uploadId: String = "sess-1",
        partNumber: Int = 1,
        etag: String = "etag-$partNumber",
        size: Long = chunkSize.toLong(),
    ): FileUploadPart = FileUploadPart(
        uploadId = uploadId,
        partNumber = partNumber,
        etag = etag,
        size = size,
    )

    /**
     * @description 构造测试用文件实体
     * @returns File 文件实体
     */
    private fun fileEntity(id: String = "f-1", userId: String = "user-1"): File = File(
        id = id,
        userId = userId,
        originalName = "movie.mp4",
        mimeType = "video/mp4",
        size = totalSize,
        path = "uploads/2026/09/20/01EXAMPLEKEY.mp4",
        url = "http://minio:9000/scx-files/uploads/2026/09/20/01EXAMPLEKEY.mp4",
    )

    /**
     * @description 任意字符串匹配器（Kotlin 非空参数需空安全兜底，避免 any() 返回 null 触发空检查）
     * @returns String 匹配占位值
     */
    private fun anyString(): String = any<String>() ?: ""

    /**
     * @description 任意整数匹配器（空安全兜底）
     * @returns Int 匹配占位值
     */
    private fun anyInt(): Int = any<Int>() ?: 0

    /**
     * @description 任意长整数匹配器（空安全兜底）
     * @returns Long 匹配占位值
     */
    private fun anyLong(): Long = any<Long>() ?: 0L

    /**
     * @description 任意会话状态匹配器（空安全兜底）
     * @returns UploadSessionStatus 匹配占位值
     */
    private fun anyStatus(): UploadSessionStatus = any(UploadSessionStatus::class.java) ?: UploadSessionStatus.UPLOADING

    /**
     * @description 任意时间匹配器（空安全兜底）
     * @returns LocalDateTime 匹配占位值
     */
    private fun anyTime(): LocalDateTime = any<LocalDateTime>() ?: LocalDateTime.now()

    /**
     * @description 任意分片内容流匹配器（空安全兜底）
     * @returns InputStream 匹配占位值
     */
    private fun anyInputStream(): java.io.InputStream = any<java.io.InputStream>() ?: emptyStream()

    /**
     * @description 任意分片清单匹配器（空安全兜底）
     * @returns List<PartEtag> 匹配占位值
     */
    private fun anyParts(): List<PartEtag> = any<List<PartEtag>>() ?: emptyList()

    /**
     * @description 空字节流（分片内容在单测中不被读取）
     * @returns InputStream 空输入流
     */
    private fun emptyStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
}
