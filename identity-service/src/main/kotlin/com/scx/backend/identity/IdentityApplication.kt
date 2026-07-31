package com.scx.backend.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 身份认证服务启动入口。用户/登录/令牌逻辑在 Step 5 迁入。
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class IdentityApplication

fun main(args: Array<String>) {
    runApplication<IdentityApplication>(*args)
}
