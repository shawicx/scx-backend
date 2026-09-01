package com.scx.backend.commonaudit.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * @description 操作日志实体（表 operation_logs）
 *
 * 记录管理端写操作（创建/更新/删除等）的审计信息：操作人、所属模块与
 * 动作、请求上下文（方法/URI/IP/UA/入参摘要）、结果（成功/失败/耗时）。
 * 不与 users 建外键——日志独立于用户生命周期，userId 仅作逻辑关联。
 */
@Entity
@Table(name = "operation_logs")
class OperationLog(

    /** ULID 主键（应用层 IdGenerator 生成） */
    @Id
    @Column(name = "id", length = 30, nullable = false)
    var id: String,

    /** 操作人用户 ID（公开接口无操作者时为 null，如注册） */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "\"userId\"", columnDefinition = "char(26)")
    var userId: String? = null,

    /** 操作人邮箱（取自网关注入的身份头，冗余存储便于检索） */
    @Column(name = "\"userEmail\"", length = 100)
    var userEmail: String? = null,

    /** 操作所属模块（如：用户管理、角色管理、文件管理） */
    @Column(name = "\"module\"", length = 64, nullable = false)
    var module: String,

    /** 操作动作（如：创建用户、批量删除） */
    @Column(name = "\"action\"", length = 64, nullable = false)
    var action: String,

    /** HTTP 请求方法（GET/POST/PUT/DELETE 等） */
    @Column(name = "\"httpMethod\"", length = 8)
    var httpMethod: String? = null,

    /** 请求 URI */
    @Column(name = "\"uri\"", length = 512)
    var uri: String? = null,

    /** 客户端 IP */
    @Column(name = "\"ip\"", length = 45)
    var ip: String? = null,

    /** 客户端 User-Agent（原样存储） */
    @Column(name = "\"userAgent\"", length = 512)
    var userAgent: String? = null,

    /** 请求入参 JSON 摘要（敏感字段已脱敏，超长截断） */
    @Column(name = "\"params\"", length = 2000)
    var params: String? = null,

    /** 是否成功（业务方法正常返回为 true） */
    @Column(name = "\"success\"", nullable = false)
    var success: Boolean,

    /** 失败时的错误消息 */
    @Column(name = "\"errorMessage\"", length = 512)
    var errorMessage: String? = null,

    /** 耗时（毫秒） */
    @Column(name = "\"costMs\"", nullable = false)
    var costMs: Long = 0,

    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
