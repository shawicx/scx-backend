package com.scx.backend.file

import com.scx.backend.common.dto.CountResultDto
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import com.scx.backend.file.dto.DeleteFilesDto
import com.scx.backend.file.dto.FileListResponseDto
import com.scx.backend.file.dto.FileResponseDto
import com.scx.backend.file.dto.QueryFilesDto
import com.scx.backend.file.entity.File
import com.scx.backend.file.repository.FileRepository
import com.scx.backend.file.storage.MinioStorageService
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @description 文件服务
 *
 * 基于 MinIO 对象存储实现文件上传（单文件/批量）、列表查询、详情与批量软删除。
 * 桶为私有，入库的 url 为逻辑地址；接口响应中的 url 为临时预签名直链。
 * 数据按当前用户隔离，管理员（isAdmin）可跨用户查询与删除。
 */
@Service
class FileService(
    private val fileRepository: FileRepository,
    private val storageService: MinioStorageService,
) {

    private val logger = LoggerFactory.getLogger(FileService::class.java)

    /** 可排序字段白名单（防任意字段注入排序） */
    private val sortableFields = setOf("createdAt", "size", "originalName", "mimeType")

    /**
     * @description 上传单个文件：上传对象 → 落库，落库失败时清理对象补偿
     * @param userId 归属用户 ID
     * @param file 上传的文件数据
     * @returns FileResponseDto 文件信息（url 为预签名直链）
     */
    @Transactional
    fun uploadFile(userId: String, file: UploadedFile): FileResponseDto {
        validateUpload(file)
        val objectKey = buildObjectKey(file.originalName)
        storageService.put(objectKey, file.mimeType, file.size, file.buffer)
        val saved = try {
            fileRepository.save(
                File(
                    id = IdGenerator.nextId(),
                    userId = userId,
                    originalName = file.originalName,
                    mimeType = file.mimeType,
                    size = file.size,
                    path = objectKey,
                    url = storageService.logicalUrl(objectKey),
                ),
            )
        } catch (ex: Exception) {
            // 落库失败补偿：移除已上传对象，避免存储泄漏
            storageService.remove(objectKey)
            throw ex
        }
        logger.info("文件上传成功: id={} userId={} key={}", saved.id, userId, objectKey)
        return FileResponseDto.from(saved, storageService.presignedGetUrl(objectKey))
    }

    /**
     * @description 批量上传文件：逐个上传 + 落库，任一失败清理已上传对象并抛出（全成功或全失败）
     * @param userId 归属用户 ID
     * @param files 上传的文件数据列表
     * @returns List<FileResponseDto> 文件信息列表（url 为预签名直链）
     */
    @Transactional
    fun uploadFiles(userId: String, files: List<UploadedFile>): List<FileResponseDto> {
        if (files.isEmpty()) return emptyList()
        val uploadedKeys = mutableListOf<String>()
        try {
            return files.map { file ->
                validateUpload(file)
                val objectKey = buildObjectKey(file.originalName)
                storageService.put(objectKey, file.mimeType, file.size, file.buffer)
                uploadedKeys.add(objectKey)
                val saved = fileRepository.save(
                    File(
                        id = IdGenerator.nextId(),
                        userId = userId,
                        originalName = file.originalName,
                        mimeType = file.mimeType,
                        size = file.size,
                        path = objectKey,
                        url = storageService.logicalUrl(objectKey),
                    ),
                )
                logger.info("文件上传成功: id={} userId={} key={}", saved.id, userId, objectKey)
                FileResponseDto.from(saved, storageService.presignedGetUrl(objectKey))
            }
        } catch (ex: Exception) {
            // 批量失败补偿：清理已上传对象，事务回滚已落库记录
            uploadedKeys.forEach { storageService.remove(it) }
            throw ex
        }
    }

    /**
     * @description 分页查询文件列表（软删除过滤 + 用户隔离）
     * @param userId 当前用户 ID
     * @param isAdmin 是否管理员（true 时不限制归属用户）
     * @param dto 查询参数（分页/搜索/MIME 过滤/排序）
     * @returns FileListResponseDto 分页结果（url 为预签名直链）
     */
    fun queryFiles(userId: String, isAdmin: Boolean, dto: QueryFilesDto): FileListResponseDto {
        val spec = Specification<File> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            // 仅查未软删除的文件
            predicates.add(cb.isNull(root.get<Any>("deletedAt")))
            // 用户隔离（管理员可查全部）
            if (!isAdmin) {
                predicates.add(cb.equal(root.get<String>("userId"), userId))
            }
            // 按原始文件名模糊搜索（不区分大小写）
            if (!dto.search.isNullOrBlank()) {
                predicates.add(
                    cb.like(cb.lower(root.get("originalName")), "%${dto.search.lowercase()}%"),
                )
            }
            // 按 MIME 类型精确过滤
            if (!dto.mimeType.isNullOrBlank()) {
                predicates.add(cb.equal(root.get<String>("mimeType"), dto.mimeType))
            }
            cb.and(*predicates.toTypedArray())
        }
        val direction = if (dto.sortOrder.equals("ASC", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val sortField = dto.sortBy.takeIf { it in sortableFields } ?: "createdAt"
        val page = dto.page.coerceAtLeast(1)
        val limit = dto.limit.coerceIn(1, 100)
        val pageable = PageRequest.of(page - 1, limit, Sort.by(direction, sortField))
        val result = fileRepository.findAll(spec, pageable)
        return FileListResponseDto(
            list = result.content.map { FileResponseDto.from(it, storageService.presignedGetUrl(it.path)) },
            total = result.totalElements,
            page = page,
            limit = limit,
        )
    }

    /**
     * @description 获取文件详情（软删除视为不存在；非管理员仅可访问本人文件）
     * @param fileId 文件 ID
     * @param userId 当前用户 ID
     * @param isAdmin 是否管理员
     * @returns FileResponseDto 文件详情（url 为预签名直链）
     */
    fun getFile(fileId: String, userId: String, isAdmin: Boolean): FileResponseDto {
        val file = fileRepository.findById(fileId)
            .orElseThrow { SystemException.dataNotFound("文件不存在或已删除") }
        if (file.deletedAt != null) {
            throw SystemException.dataNotFound("文件不存在或已删除")
        }
        if (!isAdmin && file.userId != userId) {
            throw SystemException.insufficientPermission("无权访问该文件")
        }
        return FileResponseDto.from(file, storageService.presignedGetUrl(file.path))
    }

    /**
     * @description 批量软删除文件（置 deletedAt，MinIO 对象保留；跳过非本人/已删除项）
     * @param userId 当前用户 ID
     * @param isAdmin 是否管理员（true 时可删除任意用户文件）
     * @param dto 删除请求（文件 ID 列表）
     * @returns CountResultDto count=受影响行数，message=提示信息
     */
    @Transactional
    fun deleteFiles(userId: String, isAdmin: Boolean, dto: DeleteFilesDto): CountResultDto {
        val targets = fileRepository.findAllById(dto.ids.distinct()).filter {
            it.deletedAt == null && (isAdmin || it.userId == userId)
        }
        if (targets.isNotEmpty()) {
            val now = LocalDateTime.now()
            targets.forEach { it.deletedAt = now }
            fileRepository.saveAll(targets)
        }
        logger.info("批量删除文件: 请求 {} 个，实际删除 {} 个（userId={}）", dto.ids.size, targets.size, userId)
        return CountResultDto(
            count = targets.size,
            message = "成功删除 ${targets.size} 个文件",
        )
    }

    /**
     * @description 校验上传文件的基本合法性
     * @param file 上传的文件数据
     */
    private fun validateUpload(file: UploadedFile) {
        if (file.originalName.isBlank()) {
            throw SystemException.invalidParameter("文件名不能为空")
        }
        if (file.size <= 0 || file.buffer.isEmpty()) {
            throw SystemException.invalidParameter("上传文件内容不能为空")
        }
    }

    companion object {
        private val DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        /** 扩展名白名单：1-10 位小写字母/数字 */
        private val EXTENSION_PATTERN = Regex("^[a-z0-9]{1,10}$")

        /**
         * @description 生成对象存储键：uploads/{yyyy}/{MM}/{dd}/{ULID}.{清洗后的扩展名}
         *
         * 扩展名取原始文件名最后一段并小写化，不在白名单内（含非法字符/过长/缺失）时省略，
         * 避免路径穿越与任意键注入。
         *
         * @param originalName 原始文件名
         * @returns String 对象键
         *
         * @example FileService.buildObjectKey("照片.PNG") // uploads/2026/08/30/01J….png
         */
        fun buildObjectKey(originalName: String): String {
            val datePath = LocalDate.now().format(DATE_PATH_FORMATTER)
            val ext = originalName.substringAfterLast('.', "")
                .lowercase()
                .takeIf { it.isNotEmpty() && EXTENSION_PATTERN.matches(it) }
            val ulid = IdGenerator.nextId()
            return if (ext == null) "uploads/$datePath/$ulid" else "uploads/$datePath/$ulid.$ext"
        }
    }
}

/** 上传的文件数据 */
data class UploadedFile(
    val originalName: String,
    val mimeType: String,
    val size: Int,
    val buffer: ByteArray,
)
