// ============================================================
// identity-service 模块 —— 身份认证服务
// ============================================================
// 职责：用户 CRUD、注册/登录、令牌签发与校验、角色分配、健康检查、种子数据。
// 专属表：users、user_roles。
// Step 1 阶段为空骨架，Step 5 迁入源码并完成令牌协议改造。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-web"))

    // 审计日志共享组件（操作/登录日志实体、仓库与 V2 建表迁移），
    // 日志查询接口（/api/logs/**）由本服务承载
    implementation(project(":common-audit"))

    // 过渡：identity 的 UserService/SeedService 仍直连 rbac 表（角色/权限查询），
    // 并调用 notification 发邮件。Step 6 网关化后改为 RestClient 跨服务调用，
    // 届时移除这两个依赖。
    implementation(project(":rbac-service"))
    implementation(project(":notification-service"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // OpenAPI 文档（Controller/DTO 的 @Schema/@Operation 注解 + Swagger UI）
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("com.h2database:h2")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
