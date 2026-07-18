package com.scx.backend.modules.health

import com.scx.backend.common.decorator.Public
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 健康检查控制器
 * 对标 scx-service: src/modules/health/health.controller.ts
 *
 * 路由前缀 /api/health（由 context-path=/api 提供），@Public 免鉴权。
 * 与 /api/actuator/health 互补：本端点返回业务侧结构化详情。
 */
@RestController
@RequestMapping("/health")
class HealthController(
    private val healthService: HealthService,
) {
    @Public
    @GetMapping
    fun check() = healthService.checkHealth()
}
