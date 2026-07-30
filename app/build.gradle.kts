// ============================================================
// app 模块 —— 过渡用单体（微服务拆解完成前承接全部源码）
// ============================================================
// 本模块是原 scx-backend 单体在多模块化后的临时承载处。
// 随着各业务能力被抽到独立服务模块（identity/rbac/notification/file），
// 本模块会逐步瘦身，直至 Step 7 完全退役删除。
//
// 插件、依赖与构建任务均沿用单单体时代的配置，确保过渡期行为一致。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    // 共享契约模块（响应封装/异常/ULID/令牌工具/注解/跨服务 DTO）
    implementation(project(":common"))

    // 过渡：邮件能力已抽到 notification-service，app 暂复用其 MailService 实现。
    // Step 5 将 UserService 迁入 identity-service 后，改为 RestClient 调 notification，
    // 届时移除本依赖。notification 是 Spring Boot 应用，此处仅复用其类（jar）。
    implementation(project(":notification-service"))

    // 过渡：RBAC 能力已抽到 rbac-service，app 暂复用其 Role/Permission/RolePermission
    // entity 与 repository（UserService/SeedService 仍直连这些表）。Step 5 迁移
    // UserService 到 identity 后改为 RestClient 调 rbac，届时移除本依赖。
    implementation(project(":rbac-service"))

    // Spring Boot 基础
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Jackson Kotlin 支持
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAPI 文档（springdoc 3.x 支持 Spring Boot 4）
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // 数据库
    runtimeOnly("org.postgresql:postgresql")
    // Spring Boot 4.0 将 Flyway 自动配置拆分为独立模块 spring-boot-flyway，
    // 仅引入 flyway-core 不会触发自动迁移。必须同时引入此模块。
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // 工具
    implementation("org.bouncycastle:bcprov-jdk18on:1.80") // AES/HMAC 加密工具预留

    // 测试
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("com.h2database:h2")
}

// JPA 实体默认 open（Kotlin data class 需 allOpen）
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// ============================================================
// 环境配置自动匹配（沿用单体时代逻辑）
// ============================================================
// - bootRun（本地运行）：默认 dev profile
// - 环境变量通过 systemProperty 透传给 JVM（绕开 Gradle daemon 不读新 export 的问题）
//   这样 `export MAIL_HOST=xxx && ./gradlew bootRun` 能生效
// ============================================================

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // 仅设置默认 profile；环境变量统一由 .env 文件 + spring-dotenv 加载，
    // IDEA / bootRun / java -jar / Docker 四种启动方式读取逻辑完全一致。
    val profile = System.getenv("SPRING_PROFILES_ACTIVE") ?: "dev"
    args = listOf("--spring.profiles.active=$profile")
}

// 构建打包时，在 jar 内置 prod 为默认 profile
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    environment = mapOf("SPRING_PROFILES_ACTIVE" to "prod")
}
