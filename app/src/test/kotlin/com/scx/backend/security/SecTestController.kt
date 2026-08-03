package com.scx.backend.security

import com.scx.backend.common.decorator.Public
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 安全测试专用 Controller（顶层类，确保被组件扫描且可实例化） */
@RestController
@RequestMapping("/sec")
class SecTestController {
    @GetMapping("/protected")
    fun protectedEndpoint(): Map<String, String> = mapOf("ok" to "true")

    @Public
    @GetMapping("/public")
    fun publicEndpoint(): Map<String, String> = mapOf("ok" to "true")
}
