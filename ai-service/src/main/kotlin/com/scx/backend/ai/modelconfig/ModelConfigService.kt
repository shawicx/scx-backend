package com.scx.backend.ai.modelconfig

import com.scx.backend.ai.entity.AiModelConfig
import com.scx.backend.ai.modelconfig.dto.ModelConfigResponseDto
import com.scx.backend.ai.modelconfig.dto.SaveModelConfigDto
import com.scx.backend.ai.modelconfig.dto.TestConnectionDto
import com.scx.backend.ai.modelconfig.dto.TestConnectionResultDto
import com.scx.backend.ai.provider.AiProviderClient
import com.scx.backend.ai.provider.ResolvedAiConfig
import com.scx.backend.ai.repository.AiModelConfigRepository
import com.scx.backend.common.exception.SystemException
import com.scx.backend.common.util.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @description AI 模型配置服务（每用户一条 upsert + 掩码读取 + 解密解析 + 连通性测试）
 *
 * apiKey 落库前 AES-256-GCM 加密；明文仅在内存中短暂存在，禁止落日志。
 */
@Service
class ModelConfigService(
    private val aiModelConfigRepository: AiModelConfigRepository,
    private val apiKeyCipher: ApiKeyCipher,
    private val aiProviderClient: AiProviderClient,
) {

    /**
     * @description 查询当前用户配置（未配置返回 null，apiKey 仅掩码）
     * @param userId 用户 ID
     * @return ModelConfigResponseDto? 配置响应
     */
    fun getConfig(userId: String): ModelConfigResponseDto? {
        val entity = aiModelConfigRepository.findByUserId(userId) ?: return null
        val plainKey = runCatching { apiKeyCipher.decrypt(entity.apiKey) }
            .getOrElse { return ModelConfigResponseDto.from(entity, "") }
        return ModelConfigResponseDto.from(entity, plainKey)
    }

    /**
     * @description 保存配置（userId 维度 upsert；apiKey 传空/缺省表示保留原值，首次必须提供）
     * @param userId 用户 ID
     * @param dto 保存请求
     * @return ModelConfigResponseDto 保存后的配置响应
     */
    @Transactional
    fun saveConfig(userId: String, dto: SaveModelConfigDto): ModelConfigResponseDto {
        val existing = aiModelConfigRepository.findByUserId(userId)
        val plainKey = dto.apiKey?.takeIf { it.isNotBlank() }
            ?: existing?.let { apiKeyCipher.decrypt(it.apiKey) }
            ?: throw SystemException.invalidParameter("首次配置必须填写 API Key")

        val now = LocalDateTime.now()
        val entity = existing?.apply {
            baseUrl = dto.baseUrl.trim()
            apiKey = apiKeyCipher.encrypt(plainKey)
            model = dto.model.trim()
            systemPrompt = dto.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
            temperature = dto.temperature
            updatedAt = now
        } ?: AiModelConfig(
            id = IdGenerator.nextId(),
            userId = userId,
            baseUrl = dto.baseUrl.trim(),
            apiKey = apiKeyCipher.encrypt(plainKey),
            model = dto.model.trim(),
            systemPrompt = dto.systemPrompt?.trim()?.takeIf { it.isNotEmpty() },
            temperature = dto.temperature,
            createdAt = now,
            updatedAt = now,
        )
        return ModelConfigResponseDto.from(aiModelConfigRepository.save(entity), plainKey)
    }

    /**
     * @description 解析当前用户可用配置（解密 apiKey），未配置抛 9500
     * @param userId 用户 ID
     * @return ResolvedAiConfig 已解密配置
     */
    fun resolveDecrypted(userId: String): ResolvedAiConfig {
        val entity = aiModelConfigRepository.findByUserId(userId)
            ?: throw SystemException.aiConfigMissing()
        return ResolvedAiConfig(
            baseUrl = entity.baseUrl,
            apiKey = apiKeyCipher.decrypt(entity.apiKey),
            model = entity.model,
            systemPrompt = entity.systemPrompt,
            temperature = entity.temperature,
        )
    }

    /**
     * @description 连通性测试（apiKey 传空时回退用已保存的 Key；失败不抛异常）
     * @param userId 用户 ID
     * @param dto 测试请求
     * @return TestConnectionResultDto 测试结果
     */
    fun testConnection(userId: String, dto: TestConnectionDto): TestConnectionResultDto {
        val apiKey = dto.apiKey?.takeIf { it.isNotBlank() }
            ?: aiModelConfigRepository.findByUserId(userId)?.let { apiKeyCipher.decrypt(it.apiKey) }
            ?: return TestConnectionResultDto(ok = false, latencyMs = 0, message = "尚未配置 API Key")
        return aiProviderClient.testConnection(dto.baseUrl, apiKey)
    }
}
