// ============================================================
// common 模块 —— 共享契约（纯协议层，无 Servlet/Web 依赖）
// ============================================================
// 存放跨服务复用的、与 Web 栈无关的基础设施代码：
//  - 响应封装（ApiResponse）
//  - 异常体系（SystemException / SystemErrorCode）
//  - 常量（CacheKeys / TtlConstants）
//  - 工具（IdGenerator(ULID) / CryptoUtil）
//  - 注解（@Public / @Admin）
//  - 认证契约（AuthPrincipal / TokenPayload / TokenCodec 令牌编解码 / PublicPaths 白名单）
//  - 序列化配置（JacksonConfig）
//  - 跨服务 DTO（MessageDto / CountResultDto）
//
// 本模块无 Servlet/Tomcat 依赖，可同时被 Servlet 服务与 WebFlux 网关依赖。
// Servlet 专属组件（GlobalResponseHandler 等）已拆到 common-web 模块。
// ============================================================

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Jackson（TokenCodec/JacksonConfig 需要）。用 api 暴露：TokenCodec 的方法签名
    // 暴露了 ObjectMapper，消费方编译时需要 jackson 类型可见。
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    // spring-context（JacksonConfig 的 @Configuration/@Bean）
    implementation("org.springframework:spring-context")
}
