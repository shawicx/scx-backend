// ============================================================
// file-service 模块 —— 文件服务
// ============================================================
// 职责：文件上传/查询/删除（基于 MinIO 对象存储，私有桶 + 预签名 URL 访问）。
// 专属表：files。
// Step 1 阶段为空骨架，Step 7 迁入源码。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-web"))

    // 审计日志共享组件（操作/登录日志实体、仓库与 V2 建表迁移）：
    // 共享库 flyway_schema_history 下 V2 必须与本服务解析一致，否则启动校验失败
    implementation(project(":common-audit"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // 对象存储（MinIO，S3 兼容协议）
    implementation("io.minio:minio:9.0.3")
    // OpenAPI 文档（DTO 的 @Schema/@Operation 注解 + Swagger UI）
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("com.h2database:h2")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
