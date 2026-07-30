package com.scx.backend.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 身份认证服务启动入口（骨架）。用户/登录/令牌逻辑在 Step 5 迁入。
 */
@SpringBootApplication
class IdentityApplication

fun main(args: Array<String>) {
    runApplication<IdentityApplication>(*args)
}
