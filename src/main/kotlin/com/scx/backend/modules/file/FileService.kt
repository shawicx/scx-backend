package com.scx.backend.modules.file

import com.scx.backend.modules.file.dto.DeleteFilesDto
import com.scx.backend.modules.file.dto.FileListResponseDto
import com.scx.backend.modules.file.dto.FileResponseDto
import com.scx.backend.modules.file.dto.QueryFilesDto
import org.springframework.stereotype.Service

/**
 * 文件服务（空壳）
 *
 * 所有方法暂未实现，待后续接入对象存储（OSS/S3/本地）时填充。
 */
@Service
class FileService {

    /** 上传单个文件 */
    fun uploadFile(userId: String, file: UploadedFile): FileResponseDto {
        throw NotImplementedError("Method not implemented.")
    }

    /** 批量上传文件 */
    fun uploadFiles(userId: String, files: List<UploadedFile>): List<FileResponseDto> {
        throw NotImplementedError("Method not implemented.")
    }

    /** 查询文件列表 */
    fun queryFiles(userId: String, isAdmin: Boolean, dto: QueryFilesDto): FileListResponseDto {
        throw NotImplementedError("Method not implemented.")
    }

    /** 获取文件详情 */
    fun getFile(fileId: String, userId: String, isAdmin: Boolean): FileResponseDto {
        throw NotImplementedError("Method not implemented.")
    }

    /** 批量删除文件 */
    fun deleteFiles(userId: String, isAdmin: Boolean, dto: DeleteFilesDto): Map<String, Any> {
        throw NotImplementedError("Method not implemented.")
    }
}

/** 上传的文件数据 */
data class UploadedFile(
    val originalName: String,
    val mimeType: String,
    val size: Int,
    val buffer: ByteArray,
)
