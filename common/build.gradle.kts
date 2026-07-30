// ============================================================
// common 模块 —— 共享契约（纯 Kotlin 库，非 Spring Boot 应用）
// ============================================================
// 存放跨服务复用的基础设施代码：
//  - 响应封装（ApiResponse / GlobalResponseHandler）
//  - 异常体系（SystemException / SystemErrorCode / GlobalExceptionHandler）
//  - 常量（CacheKeys / TtlConstants）
//  - 工具（IdGenerator(ULID) / CryptoUtil / IpUtils）
//  - 注解（@Public / @Admin）
//  - 认证契约（AuthPrincipal / TokenCodec 令牌编解码）
//  - 跨服务 DTO（MessageDto / CountResultDto）
//
// 本模块不打包为可执行 jar，供 gateway / 各业务服务 implementation(project(":common"))。
// Step 1 阶段为空骨架，Step 2 迁入源码。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // 响应封装的 ResponseBodyAdvice、异常处理器需要 Spring Web/Context
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
