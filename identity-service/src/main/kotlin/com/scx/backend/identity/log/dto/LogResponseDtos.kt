package com.scx.backend.identity.log.dto

import com.scx.backend.commonaudit.entity.LoginLog
import com.scx.backend.commonaudit.entity.OperationLog
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 操作日志响应 */
@Schema(description = "操作日志响应")
data class OperationLogResponseDto(
    @Schema(description = "日志 ID")
    val id: String,

    @Schema(description = "操作人用户 ID（公开接口无操作者时为空）")
    val userId: String?,

    @Schema(description = "操作人邮箱")
    val userEmail: String?,

    @Schema(description = "所属模块")
    val module: String,

    @Schema(description = "操作动作")
    val action: String,

    @Schema(description = "HTTP 请求方法")
    val httpMethod: String?,

    @Schema(description = "请求 URI")
    val uri: String?,

    @Schema(description = "客户端 IP")
    val ip: String?,

    @Schema(description = "客户端 User-Agent")
    val userAgent: String?,

    @Schema(description = "请求入参 JSON 摘要（敏感字段已脱敏）")
    val params: String?,

    @Schema(description = "是否成功")
    val success: Boolean,

    @Schema(description = "失败时的错误消息")
    val errorMessage: String?,

    @Schema(description = "耗时（毫秒）")
    val costMs: Long,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 从操作日志实体构造响应 DTO
         * @param entity 操作日志实体
         * @returns OperationLogResponseDto 操作日志响应
         *
         * @example OperationLogResponseDto.from(entity)
         */
        fun from(entity: OperationLog) = OperationLogResponseDto(
            id = entity.id,
            userId = entity.userId,
            userEmail = entity.userEmail,
            module = entity.module,
            action = entity.action,
            httpMethod = entity.httpMethod,
            uri = entity.uri,
            ip = entity.ip,
            userAgent = entity.userAgent,
            params = entity.params,
            success = entity.success,
            errorMessage = entity.errorMessage,
            costMs = entity.costMs,
            createdAt = entity.createdAt,
        )
    }
}

/** 操作日志列表响应 */
@Schema(description = "操作日志列表响应")
data class OperationLogListResponseDto(
    @Schema(description = "操作日志列表")
    val list: List<OperationLogResponseDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)

/** 登录日志响应 */
@Schema(description = "登录日志响应")
data class LoginLogResponseDto(
    @Schema(description = "日志 ID")
    val id: String,

    @Schema(description = "用户 ID（登录失败且邮箱不存在时为空）")
    val userId: String?,

    @Schema(description = "登录邮箱")
    val email: String?,

    @Schema(description = "登录类型：PASSWORD / EMAIL_CODE / LOGOUT / REFRESH")
    val loginType: String,

    @Schema(description = "是否成功")
    val success: Boolean,

    @Schema(description = "失败原因")
    val failReason: String?,

    @Schema(description = "客户端 IP")
    val ip: String?,

    @Schema(description = "客户端 User-Agent")
    val userAgent: String?,

    @Schema(description = "创建时间")
    val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * @description 从登录日志实体构造响应 DTO
         * @param entity 登录日志实体
         * @returns LoginLogResponseDto 登录日志响应
         *
         * @example LoginLogResponseDto.from(entity)
         */
        fun from(entity: LoginLog) = LoginLogResponseDto(
            id = entity.id,
            userId = entity.userId,
            email = entity.email,
            loginType = entity.loginType,
            success = entity.success,
            failReason = entity.failReason,
            ip = entity.ip,
            userAgent = entity.userAgent,
            createdAt = entity.createdAt,
        )
    }
}

/** 登录日志列表响应 */
@Schema(description = "登录日志列表响应")
data class LoginLogListResponseDto(
    @Schema(description = "登录日志列表")
    val list: List<LoginLogResponseDto>,

    @Schema(description = "总数")
    val total: Long,

    @Schema(description = "当前页码")
    val page: Int,

    @Schema(description = "每页条数")
    val limit: Int,
)
