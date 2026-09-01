package com.scx.backend.commonaudit.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 登录日志实体（表 login_logs）
 *
 * 记录登录相关事件（密码登录、验证码登录、登出、刷新令牌）的逐次明细，
 * 成功与失败均记录。userId 可空：登录失败且邮箱不存在时无对应用户。
 * 不与 users 建外键——日志独立于用户生命周期，userId 仅作逻辑关联。
 */
@Entity
@Table(name = "login_logs")
class LoginLog(

    /** ULID 主键（应用层 IdGenerator 生成） */
    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    /** 用户 ID（登录失败且邮箱不存在时为 null） */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"userId\"", columnDefinition = "char(26)")
    var userId: String? = null,

    /** 登录邮箱（请求中的邮箱，冗余存储便于检索） */
    @Column(name = "\"email\"", length = 100)
    var email: String? = null,

    /** 登录类型：PASSWORD / EMAIL_CODE / LOGOUT / REFRESH */
    @Column(name = "\"loginType\"", length = 32, nullable = false)
    var loginType: String,

    /** 是否成功 */
    @Column(name = "\"success\"", nullable = false)
    var success: Boolean,

    /** 失败原因（业务错误消息） */
    @Column(name = "\"failReason\"", length = 255)
    var failReason: String? = null,

    /** 客户端 IP */
    @Column(name = "\"ip\"", length = 45)
    var ip: String? = null,

    /** 客户端 User-Agent（原样存储） */
    @Column(name = "\"userAgent\"", length = 512)
    var userAgent: String? = null,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
