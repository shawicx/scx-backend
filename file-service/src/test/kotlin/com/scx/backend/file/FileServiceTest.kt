package com.scx.backend.file

import com.scx.backend.common.exception.SystemErrorCode
import com.scx.backend.common.exception.SystemException
import com.scx.backend.file.dto.DeleteFilesDto
import com.scx.backend.file.entity.File
import com.scx.backend.file.repository.FileRepository
import com.scx.backend.file.storage.MinioStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.LocalDateTime
import java.util.Optional

/**
 * @description 文件服务单元测试
 *
 * 纯 Mockito 单元测试（不依赖真实 MinIO 与数据库）：
 * 覆盖对象键生成与扩展名清洗、上传校验、上传落库、详情权限隔离、批量软删除归属过滤。
 */
class FileServiceTest {

    private val repository = mock(FileRepository::class.java)
    private val storageService = mock(MinioStorageService::class.java)
    private val fileService = FileService(repository, storageService)

    // ---------- 对象键生成 ----------

    @Test
    fun `buildObjectKey normalizes extension and nests by date`() {
        val key = FileService.buildObjectKey("照片.PnG")
        val parts = key.split("/")
        assertEquals("uploads", parts[0])
        assertEquals(5, parts.size, "结构应为 uploads/yyyy/MM/dd/name.ext")
        assertTrue(parts.last().endsWith(".png"), "扩展名应小写化")
    }

    @Test
    fun `buildObjectKey drops illegal extension`() {
        val key = FileService.buildObjectKey("report.pdf.exe ")
        val name = key.substringAfterLast('/')
        assertFalse(name.contains('.'), "非法扩展名（含空格）应被去除")
    }

    @Test
    fun `buildObjectKey works without extension`() {
        val key = FileService.buildObjectKey("README")
        assertEquals(5, key.split("/").size, "无扩展名时结构应为 uploads/yyyy/MM/dd/name")
        assertFalse(key.substringAfterLast('/').contains('.'), "不应带扩展名分隔符")
    }

    // ---------- 上传 ----------

    @Test
    fun `uploadFile rejects empty content`() {
        val ex = assertThrows<SystemException> {
            fileService.uploadFile("user-1", UploadedFile("a.txt", "text/plain", 0, ByteArray(0)))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
        verifyNoInteractions(storageService)
    }

    @Test
    fun `uploadFile rejects blank name`() {
        val ex = assertThrows<SystemException> {
            fileService.uploadFile("user-1", UploadedFile("", "text/plain", 3, byteArrayOf(1, 2, 3)))
        }
        assertEquals(SystemErrorCode.INVALID_PARAMETER.code, ex.code)
        verifyNoInteractions(storageService)
    }

    @Test
    fun `uploadFile stores object and returns presigned url`() {
        val saved = fileEntity(id = "f-new", userId = "user-1")
        given(storageService.logicalUrl(anyString())).willReturn("http://minio:9000/scx-files/uploads/a.png")
        given(storageService.presignedGetUrl(anyString())).willReturn("http://presigned")
        given(repository.save(any<File>())).willReturn(saved)

        val dto = fileService.uploadFile("user-1", UploadedFile("a.png", "image/png", 3, byteArrayOf(1, 2, 3)))

        assertEquals("f-new", dto.id)
        assertEquals("http://presigned", dto.url, "响应 url 应为预签名直链")
        verify(storageService).put(anyString(), anyString(), anyInt(), anyBytes())
        verify(repository).save(any())
    }

    // ---------- 详情 ----------

    @Test
    fun `getFile denies other users file when not admin`() {
        given(repository.findById("f-1")).willReturn(Optional.of(fileEntity(userId = "owner")))
        val ex = assertThrows<SystemException> { fileService.getFile("f-1", "other", false) }
        assertEquals(SystemErrorCode.INSUFFICIENT_PERMISSION.code, ex.code)
    }

    @Test
    fun `getFile allows other users file when admin`() {
        val entity = fileEntity(userId = "owner")
        given(repository.findById("f-1")).willReturn(Optional.of(entity))
        given(storageService.presignedGetUrl(entity.path)).willReturn("http://presigned")

        val dto = fileService.getFile("f-1", "admin-user", true)

        assertEquals("http://presigned", dto.url)
    }

    @Test
    fun `getFile treats soft-deleted as missing`() {
        given(repository.findById("f-1"))
            .willReturn(Optional.of(fileEntity(userId = "user-1", deletedAt = LocalDateTime.now())))
        val ex = assertThrows<SystemException> { fileService.getFile("f-1", "user-1", false) }
        assertEquals(SystemErrorCode.DATA_NOT_FOUND.code, ex.code)
    }

    // ---------- 批量删除 ----------

    @Test
    fun `deleteFiles only soft-deletes owned and undeleted files`() {
        val own = fileEntity(id = "a", userId = "user-1")
        val others = fileEntity(id = "b", userId = "user-2")
        val deleted = fileEntity(id = "c", userId = "user-1", deletedAt = LocalDateTime.now())
        given(repository.findAllById(any())).willReturn(listOf(own, others, deleted))

        val result = fileService.deleteFiles("user-1", false, DeleteFilesDto(listOf("a", "b", "c")))

        assertEquals(1, result.count, "仅本人未软删的 1 个文件应被删除")
        assertNotNull(own.deletedAt, "本人未删文件应被置删除时间")
        verify(repository).saveAll(any<MutableIterable<File>>())
    }

    @Test
    fun `deleteFiles admin can delete any users files`() {
        val others = fileEntity(id = "b", userId = "user-2")
        given(repository.findAllById(any())).willReturn(listOf(others))

        val result = fileService.deleteFiles("admin-user", true, DeleteFilesDto(listOf("b")))

        assertEquals(1, result.count)
        assertNotNull(others.deletedAt)
    }

    /**
     * @description 构造测试用文件实体
     * @param id 文件 ID
     * @param userId 归属用户 ID
     * @param deletedAt 软删除时间（默认未删除）
     * @returns File 文件实体
     */
    private fun fileEntity(
        id: String = "f-1",
        userId: String = "user-1",
        deletedAt: LocalDateTime? = null,
    ): File = File(
        id = id,
        userId = userId,
        originalName = "a.png",
        mimeType = "image/png",
        size = 3,
        path = "uploads/2026/08/30/01EXAMPLEKEY.png",
        url = "http://minio:9000/scx-files/uploads/2026/08/30/01EXAMPLEKEY.png",
        deletedAt = deletedAt,
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
     * @description 任意字节数组匹配器（空安全兜底）
     * @returns ByteArray 匹配占位值
     */
    private fun anyBytes(): ByteArray = any<ByteArray>() ?: ByteArray(0)
}
