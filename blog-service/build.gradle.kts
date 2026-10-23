// ============================================================
// blog-service 模块 —— 博客服务（公开内容 + 管理端）
// ============================================================
// 职责：博客文章（Markdown）/分类/标签管理、匿名评论（先审后显）、
// 匿名点赞与阅读统计。公开读与互动接口经网关白名单放行（PublicPaths），
// 管理端接口经 AuthGuards.requireAdmin 校验网关注入的 X-User-Admin 头。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-web"))

    // 审计日志共享组件（@OperationLog）：共享库 flyway_schema_history 校验和需一致
    implementation(project(":common-audit"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 验证（DTO 校验注解 @NotBlank/@Size）
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // OpenAPI 文档（DTO 的 @Schema 注解 + Swagger UI）
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // 集成测试用 H2（MODE=PostgreSQL）内存库跑实体/JPA 测试
    testRuntimeOnly("com.h2database:h2")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
