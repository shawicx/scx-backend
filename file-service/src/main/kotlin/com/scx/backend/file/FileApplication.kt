package com.scx.backend.file

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 文件服务启动入口。文件逻辑在 Step 7 迁入。
 *
 * 扫描根包 com.scx.backend 以发现 common-web 的共享组件
 * （GlobalResponseHandler / GlobalExceptionHandler / AccessLogInterceptor 等）。
 */
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class FileApplication

fun main(args: Array<String>) {
    runApplication<FileApplication>(*args)
}
