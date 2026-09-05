package com.scx.backend.file

import com.scx.backend.common.dto.CountResultDto
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthPrincipal
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.file.dto.DeleteFilesDto
import com.scx.backend.file.dto.FileListResponseDto
import com.scx.backend.file.dto.FileResponseDto
import com.scx.backend.file.dto.QueryFilesDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * @description 文件控制器
 *
 * 路由前缀 /api/files（由 context-path=/api 提供）。
 * 上传基于 MinIO 对象存储（私有桶，响应 url 为临时预签名直链）；
 * 查询/详情/删除基于当前登录用户隔离数据，管理员（principal.isAdmin）可跨用户操作。
 */
@Tag(name = "文件管理", description = "文件上传（单文件/批量）、查询、详情与批量删除")
@RestController
@RequestMapping("/files", produces = [MediaType.APPLICATION_JSON_VALUE])
class FileController(
    private val fileService: FileService,
) {

    @OperationLog(module = "文件管理", action = "上传文件")
    @Operation(summary = "上传文件", description = "上传单个文件到对象存储，返回文件信息（url 为临时预签名直链）")
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFile(
        @Parameter(description = "上传的文件") @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): FileResponseDto = fileService.uploadFile(requirePrincipal(principal).userId, file.toUploadedFile())

    @OperationLog(module = "文件管理", action = "批量上传文件")
    @Operation(summary = "批量上传文件", description = "一次上传多个文件到对象存储，返回文件信息列表（url 为临时预签名直链）")
    @PostMapping("/batch-upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFiles(
        @Parameter(description = "上传的文件列表") @RequestParam("files") files: List<MultipartFile>,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): List<FileResponseDto> = fileService.uploadFiles(requirePrincipal(principal).userId, files.map { it.toUploadedFile() })

    @Operation(summary = "文件列表查询", description = "分页查询当前用户的文件，支持搜索、类型过滤与排序（管理员可查全部用户）")
    @GetMapping("/list")
    fun queryFiles(dto: QueryFilesDto, @AuthenticationPrincipal principal: AuthPrincipal?): FileListResponseDto {
        val user = requirePrincipal(principal)
        return fileService.queryFiles(user.userId, user.isAdmin, dto)
    }

    @Operation(summary = "文件详情", description = "根据文件 ID 查询文件详情（url 为临时预签名直链）")
    @GetMapping("/info")
    fun getFile(
        @Parameter(description = "文件 ID") @RequestParam id: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): FileResponseDto {
        val user = requirePrincipal(principal)
        return fileService.getFile(id, user.userId, user.isAdmin)
    }

    @OperationLog(module = "文件管理", action = "批量删除文件")
    @Operation(summary = "批量删除文件", description = "逻辑删除当前用户的多个文件，返回受影响行数")
    @DeleteMapping("/batch-delete")
    fun deleteFiles(
        @Valid @RequestBody dto: DeleteFilesDto,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): CountResultDto {
        val user = requirePrincipal(principal)
        return fileService.deleteFiles(user.userId, user.isAdmin, dto)
    }

    /**
     * @description 解析认证主体；直连访问缺失网关注入的身份头时返回 401（而非 500）
     * @param principal 当前认证主体（可能为 null）
     * @returns AuthPrincipal 非空认证主体
     */
    private fun requirePrincipal(principal: AuthPrincipal?): AuthPrincipal =
        principal ?: throw SystemException.missingToken("缺少认证信息（需经网关访问，或直连调试时携带 X-User-Id 请求头）")

    /**
     * @description 将 multipart 文件转换为服务层上传数据
     * @returns UploadedFile 上传的文件数据
     */
    private fun MultipartFile.toUploadedFile(): UploadedFile = UploadedFile(
        originalName = originalFilename ?: "unnamed",
        mimeType = contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
        size = size.toInt(),
        buffer = bytes,
    )
}
