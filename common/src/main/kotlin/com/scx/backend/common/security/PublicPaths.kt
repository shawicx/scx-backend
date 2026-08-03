package com.scx.backend.common.security

/**
 * @description 公开路径白名单（无需鉴权的路由）
 *
 * 网关据此放行公开端点；下游服务的 AuthInterceptor 也据此识别 @Public 路由。
 * 微服务化后，鉴权集中在网关，下游假定网关已完成鉴权。
 *
 * 路径均以 /api 为前缀（各服务的 context-path）。
 */
object PublicPaths {

    /** Ant 风格的公开路径匹配模式（供网关 RouteLocator / 鉴权过滤器使用） */
    val PATTERNS: List<String> = listOf(
        // 用户登录/注册/验证码相关（identity 服务）
        "/api/users/register",
        "/api/users/login",
        "/api/users/login-password",
        "/api/users/encryption-key",
        "/api/users/send-login-code",
        "/api/users/send-email-code",
        "/api/users/refresh-token",
        // 邮件发送（notification 服务，全部 @Public）
        "/api/mail/**",
        // 业务健康检查（identity 服务）
        "/api/health",
        "/api/health/**",
        // Swagger / OpenAPI 文档
        "/api/swagger-ui/**",
        "/api/v3/api-docs/**",
        "/api/swagger-resources/**",
        "/api/webjars/**",
        // Spring Boot Actuator 端点
        "/api/actuator/**",
    )

    /**
     * @description 判断请求路径是否匹配公开白名单（Ant 模式）
     * @param path 请求路径
     * @param matcher 匹配函数（网关用 PathPatternParser，下游用 AntPathMatcher）
     * @return 命中任意公开模式返回 true
     */
    fun isPublic(path: String, matcher: (String) -> Boolean): Boolean =
        PATTERNS.any(matcher)
}
