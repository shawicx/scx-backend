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
 * 过渡单体启动入口。
 *
 * app 依赖各微服务模块（notification/rbac/identity 等）仅复用其类，但其它模块的
 * @SpringBootApplication 主类（带 @EnableJpaRepositories/@EnableAutoConfiguration）
 * 落在 com.scx.backend 扫描树下会被本类重复拾取，导致 bean 定义覆盖冲突。
 * 因此显式排除这些微服务主类，仅保留 common 共享组件与本单体自身逻辑。
 * Step 7 单体退役后本类一并删除。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["com.scx.backend"])
@ComponentScan(
    basePackages = ["com.scx.backend"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [
            NotificationApplication::class,
            RbacApplication::class,
            IdentityApplication::class,
        ]),
    ],
)
class ScxBackendApplication

fun main(args: Array<String>) {
    runApplication<ScxBackendApplication>(*args)
}
