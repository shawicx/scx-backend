package com.scx.backend.commonaudit

import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.context.annotation.Configuration

/**
 * @description 审计日志共享模块配置
 *
 * 宿主服务（identity / rbac / file）均以 scanBasePackages = com.scx.backend
 * 组件扫描并依赖本模块，扫描到本配置类后由 @AutoConfigurationPackage 将
 * 本模块包注册进 AutoConfigurationPackages，使 JPA 实体与 Spring Data
 * 仓库的默认扫描范围覆盖本模块（与 identity 经组件扫描发现 rbac 实体的
 * 现行机制一致）。
 *
 * 注意不使用 @EntityScan / @EnableJpaRepositories：显式指定扫描包会
 * 取代宿主服务自身包的默认扫描，导致其原有实体失效。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigurationPackage
class CommonAuditConfig
