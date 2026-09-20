package com.scx.backend.notification.repository

import com.scx.backend.notification.entity.Channel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

/**
 * @description 渠道仓储
 */
@Repository
interface ChannelRepository : JpaRepository<Channel, String>, JpaSpecificationExecutor<Channel> {
    fun findByCode(code: String): Channel?
    fun existsByCode(code: String): Boolean
    fun findByIsActiveTrue(): List<Channel>
}
