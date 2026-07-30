package com.scx.backend.common.security

/**
 * 令牌解析结果
 *
 * @property userId 用户 ID
 * @property email 用户邮箱
 * @property isAdmin 是否为管理员（旧令牌缺失该字段时为 false）
 */
data class TokenPayload(
    val userId: String,
    val email: String,
    val isAdmin: Boolean = false,
)
