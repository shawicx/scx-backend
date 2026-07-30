// ============================================================
// gateway 模块 —— API 网关
// ============================================================
// 职责：集中鉴权（验签 + 验 Redis 单点令牌）、路由、CORS。
// 将解析后的用户信息通过 X-User-* 头透传下游服务。
// Step 1 阶段为空骨架，Step 6 实现 GlobalFilter 与路由。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    // Spring Cloud 2025.1.x（Oakwood）对应 Spring Boot 4.0.x
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))

    implementation(project(":common"))
    // Spring Cloud Gateway 5.0.x（WebFlux）。
    // 注意：Spring Cloud 2025.1.x 起 artifact 名由 spring-cloud-starter-gateway 改为
    // spring-cloud-starter-gateway-server-webflux（旧的 server-mvc 为 Servlet 栈变体）。
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
