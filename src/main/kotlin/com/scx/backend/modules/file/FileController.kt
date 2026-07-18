package com.scx.backend.modules.file

import com.scx.backend.modules.file.dto.DeleteFilesDto
import com.scx.backend.modules.file.dto.QueryFilesDto
import com.scx.backend.modules.user.dto.CountResultDto
import com.scx.backend.security.AuthPrincipal
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
 * 文件控制器（空壳）
 * 对标 scx-service: src/modules/file/file.controller.ts
 *
 * 路由前缀 /api/files。源项目为未实现 stub，本项目保持一致。
 */
@RestController
@RequestMapping("/files")
class FileController(
    private val fileService: FileService,
) {
    @PostMapping("/upload")
    fun uploadFiles(): List<Any> {
        // TODO: 解析 multipart 后调 fileService
        // 源项目此处也是 stub（throw Error）
        throw NotImplementedError("File upload not implemented yet.")
    }

    @GetMapping("/list")
    fun queryFiles(dto: QueryFilesDto, @AuthenticationPrincipal principal: AuthPrincipal) =
        fileService.queryFiles(principal.userId, false, dto)

    @GetMapping("/info")
    fun getFile(@RequestParam id: String, @AuthenticationPrincipal principal: AuthPrincipal) =
        fileService.getFile(id, principal.userId, false)

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
