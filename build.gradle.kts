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

    // Jackson Kotlin 支持
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

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
