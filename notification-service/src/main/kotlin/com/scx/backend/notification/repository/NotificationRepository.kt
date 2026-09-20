package com.scx.backend.notification.repository

import com.scx.backend.notification.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

/**
 * @description 消息主表仓储
 */
@Repository
interface NotificationRepository : JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification>
