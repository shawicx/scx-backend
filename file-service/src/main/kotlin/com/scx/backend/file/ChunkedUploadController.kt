package com.scx.backend.file

import com.scx.backend.common.dto.CountResultDto
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.security.AuthPrincipal
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.file.dto.ChunkUploadResultDto
import com.scx.backend.file.dto.ChunkedUploadInitResponseDto
import com.scx.backend.file.dto.ChunkedUploadStatusDto
import com.scx.backend.file.dto.FileResponseDto
import com.scx.backend.file.dto.InitChunkedUploadDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @description 大文件分片上传控制器
 *
 * 路由前缀 /api/files/upload（由 context-path=/api 提供）。
 * 流程：init（秒传/续传/新建会话）→ PUT 分片（原始二进制流，可并发可重试）→
 * complete（MinIO 服务端合并落库）；支持进度查询与主动取消。
 * 分片大小须在 5MB-64MB，Content-Length 严格等于该分片应有大小时才被接受。
 */
@Tag(name = "大文件分片上传", description = "大文件分片上传：初始化（秒传/断点续传）、分片上传、进度查询、完成与取消")
@RestController
@RequestMapping("/files/upload", produces = [MediaType.APPLICATION_JSON_VALUE])
class ChunkedUploadController(
    private val chunkedUploadService: ChunkedUploadService,
) {

    @OperationLog(module = "文件管理", action = "初始化分片上传")
    @Operation(
        summary = "初始化分片上传",
        description = "按 SHA-256 哈希初始化：同用户已传过相同文件时秒传直接返回文件；存在未完成会话时返回已传分片号用于续传；否则新建会话",
    )
    @PostMapping("/init")
    fun initUpload(
        @Valid @RequestBody dto: InitChunkedUploadDto,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ChunkedUploadInitResponseDto = chunkedUploadService.initUpload(requirePrincipal(principal).userId, dto)

    @Operation(summary = "上传会话进度", description = "查询会话状态、已上传分片号与已上传字节数（断点恢复用）")
    @GetMapping("/sessions/{uploadId}")
    fun getStatus(
        @Parameter(description = "上传会话 ID") @PathVariable uploadId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ChunkedUploadStatusDto = chunkedUploadService.getStatus(requirePrincipal(principal).userId, uploadId)

    @Operation(
        summary = "上传分片",
        description = "请求体为分片原始二进制（application/octet-stream），Content-Length 须等于该分片应有大小（非末片=chunkSize，末片=余量）；可选 md5 查询参数与服务端 ETag 比对",
    )
    @PutMapping("/sessions/{uploadId}/chunks/{partNumber}", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun uploadChunk(
        @Parameter(description = "上传会话 ID") @PathVariable uploadId: String,
        @Parameter(description = "分片号，从 1 开始") @PathVariable partNumber: Int,
        @Parameter(description = "分片内容 MD5（可选，与服务端 ETag 比对）") @RequestParam("md5", required = false) md5: String?,
        request: HttpServletRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ChunkUploadResultDto = chunkedUploadService.uploadChunk(
        userId = requirePrincipal(principal).userId,
        uploadId = uploadId,
        partNumber = partNumber,
        declaredSize = request.contentLengthLong,
        md5 = md5,
        data = request.inputStream,
    )

    @OperationLog(module = "文件管理", action = "完成分片上传")
    @Operation(summary = "完成分片上传", description = "校验分片齐全后由 MinIO 服务端合并，落库返回文件信息（幂等：已完成会话重复调用直接返回文件）")
    @PostMapping("/sessions/{uploadId}/complete")
    fun completeUpload(
        @Parameter(description = "上传会话 ID") @PathVariable uploadId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): FileResponseDto = chunkedUploadService.completeUpload(requirePrincipal(principal).userId, uploadId)

    @OperationLog(module = "文件管理", action = "取消分片上传")
    @Operation(summary = "取消分片上传", description = "中止 MinIO 侧上传并清理分片记录（幂等）；已完成的会话不可取消")
    @DeleteMapping("/sessions/{uploadId}")
    fun abortUpload(
        @Parameter(description = "上传会话 ID") @PathVariable uploadId: String,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): CountResultDto {
        chunkedUploadService.abortUpload(requirePrincipal(principal).userId, uploadId)
        return CountResultDto(count = 1, message = "上传会话已取消")
    }

    /**
     * @description 解析认证主体；直连访问缺失网关注入的身份头时返回 401（而非 500）
     * @param principal 当前认证主体（可能为 null）
     * @returns AuthPrincipal 非空认证主体
     */
    private fun requirePrincipal(principal: AuthPrincipal?): AuthPrincipal =
        principal ?: throw SystemException.missingToken("缺少认证信息（需经网关访问，或直连调试时携带 X-User-Id 请求头）")
}
