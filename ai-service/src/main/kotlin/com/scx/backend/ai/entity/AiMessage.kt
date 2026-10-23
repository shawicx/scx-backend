package com.scx.backend.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description AI 会话消息实体（多会话：按 sessionId 归属会话，会话内按 createdAt 时间序）
 *
 * role 为 user / assistant；userId 逻辑引用 users 表（属 identity-service），
 * 仅 DDL 外键不建 JPA 实体。
 */
@Entity
@Table(name = "ai_messages")
class AiMessage(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "\"userId\"", length = 30, nullable = false)
    var userId: String,

    @Column(name = "\"sessionId\"", length = 30, nullable = false)
    var sessionId: String,

    @Column(name = "role", length = 20, nullable = false)
    var role: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
