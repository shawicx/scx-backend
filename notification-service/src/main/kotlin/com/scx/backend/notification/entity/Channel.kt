package com.scx.backend.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * @description 消息渠道实体（业务场景分类 + 投递类型）
 *
 * type 为投递类型：EMAIL（邮件投递）/ INBOX（收件箱投递）/ ANNOUNCEMENT（公告投递）。
 * isSystem=true 的三个系统渠道（EMAIL_CODE/INBOX/ANNOUNCEMENT）由种子创建、不可删除。
 */
@Entity
@Table(name = "notification_channels")
class Channel(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @Column(name = "code", length = 50, nullable = false, unique = true)
    var code: String,

    @Column(name = "name", length = 50, nullable = false)
    var name: String,

    @Column(name = "type", length = 20, nullable = false)
    var type: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "\"isSystem\"", nullable = false)
    var isSystem: Boolean = false,

    @Column(name = "\"isActive\"", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "\"updatedAt\"", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
