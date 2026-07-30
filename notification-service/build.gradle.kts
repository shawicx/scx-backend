// ============================================================
// notification-service 模块 —— 通知服务（邮件）
// ============================================================
// 职责：邮件发送（SMTP / Stub 双实现），Thymeleaf 模板渲染。
// 无数据库依赖（无状态发信）。
// Step 1 阶段为空骨架，Step 3 迁入源码。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    // 验证（DTO 校验注解 @Email/@NotBlank）
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // OpenAPI 文档（DTO 的 @Schema 注解 + Swagger UI）
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // 集成测试用 H2 占位 datasource（notification 本身无 DB 依赖）
    testRuntimeOnly("com.h2database:h2")
}
