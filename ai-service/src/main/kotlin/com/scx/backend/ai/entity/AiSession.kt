package com.scx.backend.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description AI 会话实体（多会话：每用户可建多个，按 updatedAt 倒序展示）
 *
 * title 默认取首条用户消息前 30 字符，可重命名；
 * userId 逻辑引用 users 表（属 identity-service），仅 DDL 外键不建 JPA 实体。
 */
@Entity
@Table(name = "ai_sessions")
class AiSession(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "\"userId\"", length = 30, nullable = false)
    var userId: String,

    @Column(name = "title", length = 100, nullable = false)
    var title: String,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
