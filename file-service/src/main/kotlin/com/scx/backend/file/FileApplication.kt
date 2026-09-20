package com.scx.backend.file

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * @description 文件服务启动入口
 *
 * 扫描根包 com.scx.backend 以发现 common-web 的共享组件
 * （GlobalResponseHandler / GlobalExceptionHandler / AccessLogInterceptor 等）。
 * @EnableScheduling 驱动 ChunkedUploadCleanupJob 定期清理过期分片上传会话。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = ["com.scx.backend"])
class FileApplication

fun main(args: Array<String>) {
    runApplication<FileApplication>(*args)
}
