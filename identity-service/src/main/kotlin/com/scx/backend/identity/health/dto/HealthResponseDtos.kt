package com.scx.backend.identity.health.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

/**
 * @description 健康检查响应（业务侧结构化详情）
 *
 * @property service 服务名
 * @property status 总体状态（ok / degraded / error）
 * @property timestamp 检查时间（ISO-8601）
 * @property database 数据库组件状态
 * @property redis Redis 组件状态
 * @property system 系统运行信息
 * @property responseTime 检查耗时（如 "12ms"）
 *
 * @example HealthResponseDto(service = "scx-backend", status = "ok", timestamp = "2026-09-05T10:00:00Z", database = ComponentHealthDto("ok"), redis = ComponentHealthDto("ok"), system = SystemInfoDto(javaVersion = "21", platform = "Mac OS X aarch64", uptime = 60000, availableProcessors = 10, maxMemory = 100, totalMemory = 50, freeMemory = 25, usedMemory = 25), responseTime = "12ms")
 */
@Schema(description = "健康检查响应")
data class HealthResponseDto(
    @Schema(description = "服务名")
    val service: String,

    @Schema(description = "总体状态（ok / degraded / error）")
    val status: String,

    @Schema(description = "检查时间（ISO-8601）")
    val timestamp: String,

    @Schema(description = "数据库组件状态")
    val database: ComponentHealthDto,

    @Schema(description = "Redis 组件状态")
    val redis: ComponentHealthDto,

    @Schema(description = "系统运行信息")
    val system: SystemInfoDto,

    @Schema(description = "检查耗时（如 12ms）")
    val responseTime: String,
)

/**
 * @description 组件健康状态（数据库 / Redis 等依赖）
 *
 * message 仅在异常时存在；为 null 时不输出该键，保持与历史响应结构一致。
 *
 * @property status 组件状态（ok / error）
 * @property message 异常信息（正常时为 null 且不序列化）
 *
 * @example ComponentHealthDto(status = "ok")
 */
@Schema(description = "组件健康状态")
data class ComponentHealthDto(
    @Schema(description = "组件状态（ok / error）")
    val status: String,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "异常信息（正常时不输出）")
    val message: String? = null,
)

/**
 * @description 系统运行信息（JVM 与操作系统）
 *
 * @property javaVersion Java 版本
 * @property platform 操作系统与架构
 * @property uptime JVM 运行时长（毫秒）
 * @property availableProcessors 可用处理器数
 * @property maxMemory 最大可用内存（字节）
 * @property totalMemory 已分配内存（字节）
 * @property freeMemory 空闲内存（字节）
 * @property usedMemory 已使用内存（字节）
 *
 * @example SystemInfoDto(javaVersion = "21", platform = "Mac OS X aarch64", uptime = 60000, availableProcessors = 10, maxMemory = 100, totalMemory = 50, freeMemory = 25, usedMemory = 25)
 */
@Schema(description = "系统运行信息")
data class SystemInfoDto(
    @Schema(description = "Java 版本")
    val javaVersion: String,

    @Schema(description = "操作系统与架构")
    val platform: String,

    @Schema(description = "JVM 运行时长（毫秒）")
    val uptime: Long,

    @Schema(description = "可用处理器数")
    val availableProcessors: Int,

    @Schema(description = "最大可用内存（字节）")
    val maxMemory: Long,

    @Schema(description = "已分配内存（字节）")
    val totalMemory: Long,

    @Schema(description = "空闲内存（字节）")
    val freeMemory: Long,

    @Schema(description = "已使用内存（字节）")
    val usedMemory: Long,
)
