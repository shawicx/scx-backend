package com.scx.backend.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description AI 服务启动入口（每用户模型配置 + OpenAI 兼容流式对话代理）
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class AiApplication

fun main(args: Array<String>) {
    runApplication<AiApplication>(*args)
}
