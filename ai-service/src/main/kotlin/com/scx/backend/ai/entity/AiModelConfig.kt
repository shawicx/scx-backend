package com.scx.backend.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description 用户 AI 模型配置实体（每用户一条，OpenAI 兼容协议）
 *
 * apiKey 存储 AES-256-GCM 密文（格式 base64(iv):base64(cipher)），
 * 任何接口不得返回明文。
 */
@Entity
@Table(name = "ai_model_configs")
class AiModelConfig(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "\"userId\"", length = 30, nullable = false, unique = true)
    var userId: String,

    @Column(name = "\"baseUrl\"", nullable = false, columnDefinition = "TEXT")
    var baseUrl: String,

    @Column(name = "\"apiKey\"", nullable = false, columnDefinition = "TEXT")
    var apiKey: String,

    @Column(name = "\"model\"", nullable = false, columnDefinition = "TEXT")
    var model: String,

    @Column(name = "\"systemPrompt\"", columnDefinition = "TEXT")
    var systemPrompt: String? = null,

    @Column(name = "\"temperature\"")
    var temperature: Double? = null,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
