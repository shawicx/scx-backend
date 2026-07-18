plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.jpa") version "2.2.20"
    id("org.springframework.boot") version "4.0.7"
    // Spring Boot 4.0 起，spring-boot-gradle-plugin 自带依赖管理（原生 BOM），
    // 不再需要 io.spring.dependency-management 插件。
}

group = "com.scx"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Spring Boot 4.0：用 Gradle 原生 BOM platform 管理依赖版本（替代已废弃的
    // io.spring.dependency-management 插件）。后续 starter 不必再写版本号。
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.7"))

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

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ============================================================
// 环境配置自动匹配
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
