package com.scx.backend.file

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * @description 文件服务启动入口（骨架）。文件逻辑在 Step 7 迁入。
 */
@SpringBootApplication
class FileApplication

fun main(args: Array<String>) {
    runApplication<FileApplication>(*args)
}
