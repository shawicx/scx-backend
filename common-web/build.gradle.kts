// ============================================================
// common-web 模块 —— Servlet 专属共享组件
// ============================================================
// 存放依赖 Servlet API 的跨服务复用组件：
//  - GlobalResponseHandler / GlobalExceptionHandler（@RestControllerAdvice）
//  - AccessLogInterceptor（HandlerInterceptor）
//  - AuthContextResolver（从 X-User-* 头解析身份）
//  - IpUtils（HttpServletRequest 工具）
//
// 本模块依赖 common（纯协议层），并引入 Servlet Web 栈。
// 被各 Servlet 服务（identity/rbac/notification/file）依赖；
// WebFlux 网关不依赖本模块（避免 Servlet/Tomcat 污染）。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // 用 api 传递 common，使依赖 common-web 的服务能直接访问 common 的类
    // （SystemException / IdGenerator / @Public / DTO / TokenCodec 等）
    api(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
