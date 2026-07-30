package com.scx.backend.rbac

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 角色权限服务启动入口。角色/权限逻辑在 Step 4 迁入。
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class RbacApplication

fun main(args: Array<String>) {
    runApplication<RbacApplication>(*args)
}
