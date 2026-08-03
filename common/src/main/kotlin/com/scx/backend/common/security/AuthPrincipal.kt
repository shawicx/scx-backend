package com.scx.backend.common.security

/**
 * 认证主体信息
 *
 * SecurityContext 中存储的认证主体（由令牌解析得到）。
 * 微服务化后，网关解析令牌并通过 X-User-* 头透传，下游服务据此重建本主体。
 *
 * @property userId 用户 ID（ULID）
 * @property email 用户邮箱
 * @property isAdmin 是否为管理员（令牌嵌入；旧令牌缺失时默认 false）
 */
data class AuthPrincipal(
    val userId: String,
    val email: String,
    val isAdmin: Boolean = false,
)
