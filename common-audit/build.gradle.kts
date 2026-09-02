// ============================================================
// common-audit 模块 —— 审计日志共享组件
// ============================================================
// 存放操作日志 / 登录日志的跨服务复用组件：
//  - OperationLog / LoginLog 实体与仓库（表：operation_logs / login_logs）
//  - V2__audit_logs.sql 建表迁移（凡依赖本模块的服务均从 classpath
//    解析到同一份 V2，保证共享库 flyway_schema_history 校验和一致）
//
// 本模块依赖 common-web（Servlet 专属共享组件），并引入 JPA 栈。
// 仅供有数据库的服务（identity / rbac / file）依赖；
// notification（无数据库）与 WebFlux 网关不依赖本模块。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `java-library`
}

dependencies {
    // 用 api 传递 common-web/common，使依赖本模块的服务能直接访问
    // AuthContextResolver / IpUtils / SystemException / IdGenerator 等
    api(project(":common-web"))
    // ClientInfo 扩展函数需要 Servlet API（common-web 的 starter-web 为 implementation，不传递）
    implementation("jakarta.servlet:jakarta.servlet-api")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
