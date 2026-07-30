package com.scx.backend.common.dto

/**
 * @description 通用消息响应（跨服务复用）
 *
 * 用于仅需返回提示消息的接口。Spring 的 GlobalResponseHandler 会自动提取
 * Map 中的 message 字段；本 DTO 同样遵循该约定。
 *
 * @property message 提示消息
 *
 * @example MessageDto("操作成功")
 */
data class MessageDto(
    val message: String,
)

/**
 * @description 批量操作结果响应（跨服务复用）
 *
 * 用于需要返回受影响行数 + 提示消息的批量接口。
 *
 * @property count 受影响行数
 * @property message 提示消息
 *
 * @example CountResultDto(3, "删除成功")
 */
data class CountResultDto(
    val count: Int,
    val message: String,
)
