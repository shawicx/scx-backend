package com.scx.backend.blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 博客服务启动入口（公开内容 + 管理端：文章/分类/标签/评论/互动统计）
 *
 * 扫描根包 com.scx.backend 以发现 common 模块的共享配置与组件
 * （JacksonConfig / GlobalResponseHandler / GlobalExceptionHandler 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class BlogApplication

fun main(args: Array<String>) {
    runApplication<BlogApplication>(*args)
}
