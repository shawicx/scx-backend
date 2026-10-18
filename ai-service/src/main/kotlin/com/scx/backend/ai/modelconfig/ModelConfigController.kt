package com.scx.backend.ai.modelconfig

import com.scx.backend.ai.modelconfig.dto.ModelConfigResponseDto
import com.scx.backend.ai.modelconfig.dto.SaveModelConfigDto
import com.scx.backend.ai.modelconfig.dto.TestConnectionDto
import com.scx.backend.ai.modelconfig.dto.TestConnectionResultDto
import com.scx.backend.ai.security.AuthGuards
import com.scx.backend.commonaudit.annotation.OperationLog
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @description AI 模型配置控制器（用户级：个人中心「AI 模型设置」）
 *
 * 路由前缀 /api/ai/config（context-path=/api）；身份经网关注入的 X-User-* 头解析。
 */
@Tag(name = "AI 模型配置", description = "每用户的 OpenAI 兼容模型配置（apiKey 掩码返回）")
@RestController
@RequestMapping("/ai/config", produces = [MediaType.APPLICATION_JSON_VALUE])
class ModelConfigController(
    private val modelConfigService: ModelConfigService,
) {

    @Operation(summary = "查询我的配置", description = "未配置时 data 为 null；apiKey 仅返回掩码")
    @GetMapping
    fun get(request: HttpServletRequest): ModelConfigResponseDto? {
        val userId = AuthGuards.requireUserId(request)
        return modelConfigService.getConfig(userId)
    }

    @OperationLog(module = "AI 模型配置", action = "保存配置")
    @Operation(summary = "保存配置", description = "userId 维度 upsert；apiKey 已保存过且传空表示保留原值")
    @PostMapping("/save")
    fun save(@Valid @RequestBody dto: SaveModelConfigDto, request: HttpServletRequest): ModelConfigResponseDto {
        val userId = AuthGuards.requireUserId(request)
        return modelConfigService.saveConfig(userId, dto)
    }

    @Operation(summary = "连通性测试", description = "用提交的 baseUrl + apiKey 调用 {baseUrl}/models（apiKey 传空时回退已保存值）")
    @PostMapping("/test")
    fun test(@Valid @RequestBody dto: TestConnectionDto, request: HttpServletRequest): TestConnectionResultDto {
        val userId = AuthGuards.requireUserId(request)
        return modelConfigService.testConnection(userId, dto)
    }
}
