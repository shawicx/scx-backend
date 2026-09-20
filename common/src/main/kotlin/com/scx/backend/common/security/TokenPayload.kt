package com.scx.backend.common.security

/**
 * 令牌解析结果
 *
 * @property userId 用户 ID
 * @property email 用户邮箱
 * @property isAdmin 是否为管理员（旧令牌缺失该字段时为 false）
 * @property dataScope 数据权限范围（旧令牌缺失该字段时为 SELF）
 */
data class TokenPayload(
    val userId: String,
    val email: String,
    val isAdmin: Boolean = false,
    val dataScope: DataScope = DataScope.SELF,
)
