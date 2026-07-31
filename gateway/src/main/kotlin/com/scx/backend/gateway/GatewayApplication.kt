package com.scx.backend.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description API 网关启动入口。
 *
 * 集中鉴权（AuthGlobalFilter）+ 路由 + CORS。
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置（JacksonConfig 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
