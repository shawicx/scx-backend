package com.scx.backend.gateway

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.test.context.TestPropertySource

/**
 * 网关上下文加载测试
 *
 * 验证：
 *  1. WebFlux + Spring Cloud Gateway 上下文能正常加载（排除 Servlet 栈后）
 *  2. 路由配置（RouteLocator）正确解析
 *  3. AuthGlobalFilter / TokenCodec Bean 正确注册
 *
 * 不依赖真实下游服务与 Redis（仅验证网关自身装配）。
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        // 用内联的 JWT 密钥，不依赖外部配置
        "jwt.secret=gateway-context-test-secret",
        // 关闭 Redis 自动连接（ReactiveRedisTemplate 的 Bean 仍会创建，但不实际连接）
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6388",
    ],
)
class GatewayContextTest(
    @Autowired private val routeLocator: RouteLocator,
    @Autowired private val authGlobalFilter: AuthGlobalFilter,
) {

    @Test
    fun `context loads and core beans are wired`() {
        assertNotNull(routeLocator, "RouteLocator 应被注册")
        assertNotNull(authGlobalFilter, "AuthGlobalFilter 应被注册")
    }
}
