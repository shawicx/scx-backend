package com.scx.backend.ai.modelconfig.dto

import com.scx.backend.ai.entity.AiModelConfig
import com.scx.backend.ai.modelconfig.ApiKeyCipher
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * @description AI 模型配置相关 DTO（保存 / 响应 / 连通性测试）
 */

/** 保存 AI 模型配置请求（userId 维度 upsert；apiKey 已保存过且传空表示保留原值） */
@Schema(description = "保存 AI 模型配置请求")
data class SaveModelConfigDto(
    @Schema(description = "OpenAI 兼容 API 地址（如 https://dashscope.aliyuncs.com/compatible-mode/v1）", required = true)
    @field:NotBlank(message = "API 地址不能为空")
    @field:Size(max = 500, message = "API 地址不能超过500个字符")
    val baseUrl: String,

    @Schema(description = "API Key（首次配置必填；已保存过且传空表示保留原值）")
    @field:Size(max = 300, message = "API Key 不能超过300个字符")
    val apiKey: String? = null,

    @Schema(description = "模型名（如 qwen-plus / deepseek-chat / glm-4）", required = true)
    @field:NotBlank(message = "模型名不能为空")
    @field:Size(max = 100, message = "模型名不能超过100个字符")
    val model: String,

    @Schema(description = "系统提示词（可选）")
    @field:Size(max = 4000, message = "系统提示词不能超过4000个字符")
    val systemPrompt: String? = null,

    @Schema(description = "采样温度 0-2（可选）")
    @field:DecimalMin(value = "0.0", message = "温度不能小于0")
    @field:DecimalMax(value = "2.0", message = "温度不能大于2")
    val temperature: Double? = null,
)

/** AI 模型配置响应（apiKey 仅返回掩码，永不返回明文/密文） */
@Schema(description = "AI 模型配置响应")
data class ModelConfigResponseDto(
    @Schema(description = "配置 ID") val id: String,
    @Schema(description = "OpenAI 兼容 API 地址") val baseUrl: String,
    @Schema(description = "模型名") val model: String,
    @Schema(description = "系统提示词") val systemPrompt: String?,
    @Schema(description = "采样温度") val temperature: Double?,
    @Schema(description = "是否已配置 API Key") val hasApiKey: Boolean,
    @Schema(description = "API Key 掩码（如 sk-…ab12）") val apiKeyMasked: String?,
    @Schema(description = "创建时间") val createdAt: LocalDateTime,
    @Schema(description = "更新时间") val updatedAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 实体 + 明文 Key 转响应 DTO（明文仅用于生成掩码，不落日志）
         * @param entity 配置实体
         * @param plainKey API Key 明文
         * @return ModelConfigResponseDto 配置响应
         */
        fun from(entity: AiModelConfig, plainKey: String): ModelConfigResponseDto = ModelConfigResponseDto(
            id = entity.id,
            baseUrl = entity.baseUrl,
            model = entity.model,
            systemPrompt = entity.systemPrompt,
            temperature = entity.temperature,
            hasApiKey = true,
            apiKeyMasked = ApiKeyCipher.mask(plainKey),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

/** 连通性测试请求（apiKey 传空时回退用已保存的 Key） */
@Schema(description = "连通性测试请求")
data class TestConnectionDto(
    @Schema(description = "OpenAI 兼容 API 地址", required = true)
    @field:NotBlank(message = "API 地址不能为空")
    @field:Size(max = 500, message = "API 地址不能超过500个字符")
    val baseUrl: String,

    @Schema(description = "API Key（传空时使用已保存的 Key）")
    @field:Size(max = 300, message = "API Key 不能超过300个字符")
    val apiKey: String? = null,
)

/** 连通性测试结果 */
@Schema(description = "连通性测试结果")
data class TestConnectionResultDto(
    @Schema(description = "是否连通") val ok: Boolean,
    @Schema(description = "耗时（毫秒）") val latencyMs: Long,
    @Schema(description = "结果说明") val message: String,
)
