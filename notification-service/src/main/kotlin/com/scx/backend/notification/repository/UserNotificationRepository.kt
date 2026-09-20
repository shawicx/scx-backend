package com.scx.backend.notification.repository

import com.scx.backend.notification.entity.UserNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * @description 用户投递记录仓储（收件箱 / 未读数 / 已读标记）
 */
@Repository
interface UserNotificationRepository : JpaRepository<UserNotification, String> {

    /** 按 userId + notificationId 精确查找（标记已读用） */
    @Query(
        """
        SELECT un FROM UserNotification un
        WHERE un.userId = :userId AND un.notification.id = :notificationId
        """,
    )
    fun findByUserIdAndNotificationId(
        @Param("userId") userId: String,
        @Param("notificationId") notificationId: String,
    ): UserNotification?

    /** 分页查询某投递类型的用户消息（INBOX：未读在前、时间倒序） */
    @Query(
        value = """
            SELECT un FROM UserNotification un
            JOIN FETCH un.notification n
            WHERE un.userId = :userId AND n.channel.type = :channelType
            ORDER BY CASE WHEN un.readAt IS NULL THEN 0 ELSE 1 END ASC, n.createdAt DESC
            """,
        countQuery = """
            SELECT count(un) FROM UserNotification un
            JOIN un.notification n
            WHERE un.userId = :userId AND n.channel.type = :channelType
            """,
    )
    fun findPageByUserIdAndChannelType(
        @Param("userId") userId: String,
        @Param("channelType") channelType: String,
        pageable: Pageable,
    ): Page<UserNotification>

    /** 公告分页（ANNOUNCEMENT：置顶在前、时间倒序） */
    @Query(
        value = """
            SELECT un FROM UserNotification un
            JOIN FETCH un.notification n
            WHERE un.userId = :userId AND n.channel.type = 'ANNOUNCEMENT'
            ORDER BY n.pinned DESC, n.createdAt DESC
            """,
        countQuery = """
            SELECT count(un) FROM UserNotification un
            JOIN un.notification n
            WHERE un.userId = :userId AND n.channel.type = 'ANNOUNCEMENT'
            """,
    )
    fun findAnnouncementPageByUserId(
        @Param("userId") userId: String,
        pageable: Pageable,
    ): Page<UserNotification>

    /** 统计某投递类型的未读数（未读角标分桶） */
    @Query(
        """
        SELECT count(un) FROM UserNotification un
        JOIN un.notification n
        WHERE un.userId = :userId AND un.readAt IS NULL AND n.channel.type = :channelType
        """,
    )
    fun countUnreadByUserIdAndChannelType(
        @Param("userId") userId: String,
        @Param("channelType") channelType: String,
    ): Long

    /** 全部标记已读 */
    @Modifying
    @Query(
        """
        UPDATE UserNotification un SET un.readAt = CURRENT_TIMESTAMP
        WHERE un.userId = :userId AND un.readAt IS NULL
        """,
    )
    fun markAllRead(@Param("userId") userId: String): Int

    /** 按渠道范围全部标记已读（渠道 ID 列表非空时调用） */
    @Modifying
    @Query(
        """
        UPDATE UserNotification un SET un.readAt = CURRENT_TIMESTAMP
        WHERE un.userId = :userId AND un.readAt IS NULL
          AND un.notification.channel.id IN :channelIds
        """,
    )
    fun markAllReadByChannels(
        @Param("userId") userId: String,
        @Param("channelIds") channelIds: List<String>,
    ): Int
}
