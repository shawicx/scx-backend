package com.scx.backend

import com.scx.backend.identity.IdentityApplication
import com.scx.backend.notification.NotificationApplication
import com.scx.backend.rbac.RbacApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.boot.runApplication

/**
 * 过渡单体启动入口（兼集成测试聚合模块）。
 *
 * app 依赖各微服务模块（notification/rbac/identity/file 等）复用其类，但需排除两类冲突源：
 *  1. 各模块的 @SpringBootApplication 主类（带 @EnableJpaRepositories，会重复注册 repository bean）
 *  2. rbac/file 的 SecurityConfig（与 identity 的 SecurityConfig 同名 securityFilterChain bean 冲突）
 *
 * app 聚合测试以 identity 的鉴权链为主，故保留 identity.security.SecurityConfig，
 * 排除其它服务的安全配置（用正则按包名过滤）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["com.scx.backend"])
@ComponentScan(
    basePackages = ["com.scx.backend"],
    excludeFilters = [
        // 排除各微服务启动主类（避免重复 JPA/auto-config 注册）
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [
            NotificationApplication::class,
            RbacApplication::class,
            IdentityApplication::class,
        ]),
        // 排除 rbac/file 的安全配置（与 identity 的 securityFilterChain bean 冲突）
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.scx\\.backend\\.rbac\\.security\\..*", "com\\.scx\\.backend\\.file\\.security\\..*"]),
    ],
)
class ScxBackendApplication

fun main(args: Array<String>) {
    runApplication<ScxBackendApplication>(*args)
}
