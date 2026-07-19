package com.scx.backend.modules.file

import com.scx.backend.modules.file.dto.DeleteFilesDto
import com.scx.backend.modules.file.dto.QueryFilesDto
import com.scx.backend.modules.user.dto.CountResultDto
import com.scx.backend.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 文件控制器
 *
 * 路由前缀 /api/files（由 context-path=/api 提供）
 * 上传接口暂为未实现占位，其余接口基于当前登录用户隔离数据
 */
@Tag(name = "文件管理", description = "文件查询、详情与批量删除（上传暂未实现）")
@RestController
@RequestMapping("/files")
class FileController(
    private val fileService: FileService,
) {
    @Operation(summary = "上传文件", description = "解析 multipart 请求并保存文件。当前为未实现占位")
    @PostMapping("/upload")
    fun uploadFiles(): List<Any> {
        // TODO: 解析 multipart 后调 fileService
        throw NotImplementedError("File upload not implemented yet.")
    }

    @Operation(summary = "文件列表查询", description = "分页查询当前用户的文件，支持搜索、类型过滤与排序")
    @GetMapping("/list")
    fun queryFiles(dto: QueryFilesDto, @AuthenticationPrincipal principal: AuthPrincipal) =
        fileService.queryFiles(principal.userId, false, dto)

    @Operation(summary = "文件详情", description = "根据文件 ID 查询当前用户的文件详情")
    @GetMapping("/info")
    fun getFile(
        @Parameter(description = "文件 ID") @RequestParam id: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = fileService.getFile(id, principal.userId, false)

    @Operation(summary = "批量删除文件", description = "逻辑删除当前用户的多个文件，返回受影响行数")
    @DeleteMapping("/batch-delete")
    fun deleteFiles(
        @Valid @RequestBody dto: DeleteFilesDto,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): CountResultDto {
        val result = fileService.deleteFiles(principal.userId, false, dto)
        @Suppress("UNCHECKED_CAST")
        return CountResultDto(result["count"] as Int, result["message"] as String)
    }
}
