package com.scx.backend.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description 消息主表实体（发送记录 + 审计）
 *
 * targetType：ALL（全体广播）/ USERS（指定用户）/ ROLES（指定角色）/ DIRECT（事务邮件单发）。
 * targetIds 为目标 ID 的 JSON 数组字符串（仅审计，不用于查询）；
 * user_notifications 不在本实体做关联（按 userId 扇出行单独管理）。
 */
@Entity
@Table(name = "notifications")
class Notification(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"channelId\"", nullable = false)
    var channel: Channel,

    @Column(name = "title", length = 200, nullable = false)
    var title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "link", length = 200)
    var link: String? = null,

    @Column(name = "level", length = 20, nullable = false)
    var level: String = "INFO",

    @Column(name = "pinned", nullable = false)
    var pinned: Boolean = false,

    @Column(name = "targetType", length = 20, nullable = false)
    var targetType: String,

    @Column(name = "targetIds", columnDefinition = "TEXT")
    var targetIds: String? = null,

    @Column(name = "\"recipientEmail\"", length = 100)
    var recipientEmail: String? = null,

    @Column(name = "\"recipientCount\"", nullable = false)
    var recipientCount: Int = 0,

    @Column(name = "\"senderId\"", length = 30)
    var senderId: String? = null,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
