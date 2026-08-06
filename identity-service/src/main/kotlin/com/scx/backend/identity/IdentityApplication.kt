package com.scx.backend.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 身份认证服务启动入口。用户/登录/令牌逻辑在 Step 5 迁入。
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 *
 * 过渡期 identity 依赖 rbac-service / notification-service（UserService 直连 rbac 表、
 * 调 notification 发邮件）。rbac/file 的 SecurityConfig 用 @ConditionalOnMissingBean 标注，
 * 在 identity（已有自己的 SecurityFilterChain）中自动跳过，避免同名 bean 冲突。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class IdentityApplication

fun main(args: Array<String>) {
    runApplication<IdentityApplication>(*args)
}
