// ============================================================
// gateway 模块 —— API 网关
// ============================================================
// 职责：集中鉴权（验签 + 验 Redis 单点令牌）、路由、CORS。
// 将解析后的用户信息通过 X-User-* 头透传下游服务。
// 技术栈：Spring Cloud Gateway（WebFlux 响应式栈）。
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
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // common 模块传递引入了 spring-boot-starter-web（Tomcat/Servlet 栈）。
    // 网关强制 WebFlux（Netty），必须排除 Servlet 容器，否则 Spring Boot 会优先选 Tomcat
    // 导致 Spring Cloud Gateway 启动失败。
    // 注意：不能排除 spring-boot-starter-web 本身——common 的 GlobalResponseHandler 等
    // 类引用了 Servlet API（ResponseBodyAdvice），类路径缺失会导致 common 加载失败。
    // 因此仅排除 Tomcat embed，保留 Servlet API 类在类路径（不启动 Servlet 容器）。
    configurations {
        all {
            exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

// bootBuildImage 的 prod profile 与线程数配置由根 build.gradle.kts 的 subprojects 统一处理。
// 网关作为 WebFlux 服务，线程数 50 足够（reactive 栈不依赖大量请求线程）。
