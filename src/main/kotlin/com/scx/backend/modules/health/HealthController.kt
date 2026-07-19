package com.scx.backend.modules.health

import com.scx.backend.common.decorator.Public
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 健康检查控制器
 *
 * 路由前缀 /api/health（由 context-path=/api 提供），@Public 免鉴权。
 * 与 /api/actuator/health 互补：本端点返回业务侧结构化详情。
 */
@Tag(name = "健康检查", description = "业务侧健康状态检查")
@RestController
@RequestMapping("/health")
class HealthController(
    private val healthService: HealthService,
) {
    @Public
    @Operation(summary = "健康检查", description = "返回服务及各依赖组件（数据库、缓存等）的健康状态")
    @GetMapping
    fun check() = healthService.checkHealth()
}
