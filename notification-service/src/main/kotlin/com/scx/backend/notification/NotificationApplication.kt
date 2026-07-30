package com.scx.backend.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 通知服务启动入口。邮件逻辑在 Step 3 迁入。
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class NotificationApplication

fun main(args: Array<String>) {
    runApplication<NotificationApplication>(*args)
}
