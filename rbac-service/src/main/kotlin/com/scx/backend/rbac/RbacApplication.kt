package com.scx.backend.rbac

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 角色权限服务启动入口（骨架）。角色/权限逻辑在 Step 4 迁入。
 */
@SpringBootApplication
class RbacApplication

fun main(args: Array<String>) {
    runApplication<RbacApplication>(*args)
}
