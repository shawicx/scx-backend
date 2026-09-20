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
 * @description 用户投递记录实体（写扩散扇出行）
 *
 * 一条消息对每个收件人一行；readAt 为空表示未读。
 * userId 逻辑引用 identity 的 users 表（共享库，不建跨服务实体）。
 */
@Entity
@Table(name = "user_notifications")
class UserNotification(

    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"notificationId\"", nullable = false)
    var notification: Notification,

    @Column(name = "\"userId\"", nullable = false, length = 30)
    var userId: String,

    @Column(name = "\"readAt\"")
    var readAt: LocalDateTime? = null,
)
