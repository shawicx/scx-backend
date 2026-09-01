package com.scx.backend.identity.log.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

/** 操作日志查询参数 */
@Schema(description = "操作日志查询参数")
data class QueryOperationLogsDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数（1-100）", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字（按模块/动作/操作人邮箱/URI 模糊匹配）")
    val search: String? = null,

    @Schema(description = "按模块精确过滤（如：用户管理）")
    val module: String? = null,

    @Schema(description = "按动作精确过滤（如：创建用户）")
    val action: String? = null,

    @Schema(description = "按操作人用户 ID 精确过滤")
    val userId: String? = null,

    @Schema(description = "按成功与否过滤")
    val success: Boolean? = null,

    @Schema(description = "创建时间起（yyyy-MM-dd HH:mm:ss）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val startTime: LocalDateTime? = null,

    @Schema(description = "创建时间止（yyyy-MM-dd HH:mm:ss）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val endTime: LocalDateTime? = null,

    @Schema(description = "排序字段", defaultValue = "createdAt")
    val sortBy: String = "createdAt",

    @Schema(description = "排序方向：ASC / DESC", defaultValue = "DESC")
    val sortOrder: String = "DESC",
)

/** 登录日志查询参数 */
@Schema(description = "登录日志查询参数")
data class QueryLoginLogsDto(
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    val page: Int = 1,

    @Schema(description = "每页条数（1-100）", defaultValue = "10")
    val limit: Int = 10,

    @Schema(description = "搜索关键字（按邮箱/IP 模糊匹配）")
    val search: String? = null,

    @Schema(description = "按登录类型精确过滤：PASSWORD / EMAIL_CODE / LOGOUT / REFRESH")
    val loginType: String? = null,

    @Schema(description = "按用户 ID 精确过滤")
    val userId: String? = null,

    @Schema(description = "按成功与否过滤")
    val success: Boolean? = null,

    @Schema(description = "创建时间起（yyyy-MM-dd HH:mm:ss）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val startTime: LocalDateTime? = null,

    @Schema(description = "创建时间止（yyyy-MM-dd HH:mm:ss）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val endTime: LocalDateTime? = null,

    @Schema(description = "排序字段", defaultValue = "createdAt")
    val sortBy: String = "createdAt",

    @Schema(description = "排序方向：ASC / DESC", defaultValue = "DESC")
    val sortOrder: String = "DESC",
)
