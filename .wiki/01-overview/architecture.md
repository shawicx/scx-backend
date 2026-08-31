# 架构概览

## 微服务拓扑

```
                        客户端
                          │
                          ▼
                ┌──────────────────┐
                │ gateway (8080)   │  路由 + 集中鉴权 + CORS
                │ SCG WebFlux      │  AuthGlobalFilter
                └───┬──┬──┬──┬─────┘
        ┌────────────┘  │  │  └──────────────┐
        ▼               ▼  ▼                 ▼
 ┌─────────────┐ ┌─────────────┐ ┌──────────────────┐ ┌─────────────┐
 │ identity    │ │ rbac        │ │ notification     │ │ file        │
 │ (3001)      │ │ (3002)      │ │ (3003)           │ │ (3004)      │
 │ 用户/令牌/  │ │ 角色/权限   │ │ 邮件(SMTP/Stub)  │ │ MinIO 文件  │
 │ 健康/种子   │ │             │ │ （无数据库）      │ │             │
 └──┬────┬─────┘ └──────┬──────┘ └────────┬─────────┘ └──┬────┬─────┘
    │    │              │                 │              │    │
    ▼    │              ▼                 ▼              ▼    │
 PostgreSQL ◄───────────┘               SMTP          MinIO ◄──┘
 (5433, 6388 Redis 同样被 gateway / identity 使用)
```

- **唯一对外入口是网关 8080**；下游服务端口供内部通信与本地直连调试（本地各服务 Swagger 直连访问）。
- 全仓库当前**没有** RestClient / WebClient 服务间调用；identity 过渡期以 Gradle 依赖方式进程内耦合 rbac / notification（见下文）。

## Gradle 模块依赖

```
            common（纯协议层：TokenCodec / 错误码 / 响应 / 常量 / 工具）
             ▲                ▲
             │                │
        common-web        gateway ──────────► Redis（验单点令牌）
   （Servlet 基础设施：全局    │
     异常/响应处理、身份解析）  │ jwt.secret 与 identity 一致
             ▲                │
             ├────────┬───────┴─────┬─────────────┐
             │        │             │             │
        identity-service ──project──► rbac-service, notification-service
        （过渡期进程内耦合：直连 rbac 的 roles/permissions 表与 MailService）
             │        │             │             │
        rbac-service  notification-service    file-service
                                              （均依赖 common-web）
```

依赖关系要点（以各模块 `build.gradle.kts` 为准）：

| 模块 | 依赖 | 说明 |
| --- | --- | --- |
| `common` | 仅 jackson-module-kotlin + spring-context | 无 Servlet，可被 WebFlux 网关与 Servlet 服务共用 |
| `common-web` | `api(project(":common"))` + starter-web/validation | 全局异常/响应处理所在地 |
| `gateway` | common + SCG + data-redis-reactive（排除 tomcat） | 网关也连 Redis（单点令牌校验） |
| `identity-service` | common-web + **`project(":rbac-service")` + `project(":notification-service")`** | 过渡期耦合；含 JPA/Redis/Security/Flyway/PostgreSQL |
| `rbac-service` | common-web + JPA/Security/Flyway | 无 Redis |
| `notification-service` | common-web + mail/thymeleaf | 无数据库、无 Spring Security |
| `file-service` | common-web + JPA/Security/Flyway + MinIO SDK | 无 Redis |

**过渡期设计**（来自微服务迁移规范，代码中注释亦有标注）：

- identity 的 `UserService` 直接注入 rbac 的 `RoleRepository` / `PermissionRepository` 与 notification 的 `MailService`（`identity-service/src/main/kotlin/com/scx/backend/identity/user/UserService.kt`），后续计划改为 RestClient 调用（spec 中标注 Step 6/8）。
- 由于 identity 打包了 rbac / notification 的 Bean，其 Swagger 用 `springdoc.packages-to-scan: com.scx.backend.identity` 限定文档只含自身接口（`identity-service/src/main/resources/application.yml`）。
- rbac / file 的 `SecurityFilterChain` 标注 `@ConditionalOnMissingBean`，被 identity 进程内复用时自动让位，避免 Bean 冲突。

## 请求生命周期

以一次经网关的认证请求为例：

```
1. 客户端 → gateway:8080/api/users/list（携带 Authorization: Bearer <token>）
2. gateway AuthGlobalFilter（HIGHEST_PRECEDENCE）
   ├─ 白名单路径（PublicPaths）→ 清理伪造 X-User-* 头后直接放行
   ├─ TokenCodec 验签（HMAC-SHA256，密钥与 identity 共享）→ 解析 userId/email/isAdmin
   ├─ Redis 单点令牌比对：access_token:{userId} 必须与请求令牌完全相等
   ├─ 失败 → 401 + 统一错误体（业务码 9000）
   └─ 通过 → 清理请求原有 X-User-* 头，注入受信任的
      X-User-Id / X-User-Email / X-User-Admin 后转发
3. 路由匹配（spring.cloud.gateway.server.webflux.routes）→ 下游服务 /api 前缀
4. 下游 Spring Security FilterChain
   ├─ identity：TokenAuthenticationFilter（X-User-* 头优先，本地 Bearer 兜底）写入 SecurityContext
   ├─ rbac / file：HeaderAuthFilter 从 X-User-* 头重建身份
   └─ 均为 anyRequest().permitAll()（强制鉴权集中在网关）
5. Controller（@Valid 参数校验）→ Service（@Transactional 业务）→ Repository
6. GlobalResponseHandler（common-web）将返回值包装为统一 ApiResponse
7. 网关 DedupeResponseHeader 去重 CORS 双头 → 返回客户端
```

**特殊路径**（跳过鉴权与响应包装）：`/api/v3/api-docs*`、`/api/swagger*`、`/api/webjars*`、`/api/actuator/**`。

## 技术选型说明

| 关注点 | 选型 | 理由 |
| --- | --- | --- |
| 微服务通信 | REST + 网关（暂无注册中心） | 服务规模小，环境变量配置下游地址即可 |
| 网关 | Spring Cloud Gateway（WebFlux） | 响应式非阻塞，配合 ReactiveStringRedisTemplate 做令牌校验 |
| 鉴权 | 网关集中验签 + 下游信任 `X-User-*` 头 | 下游零令牌解析、零 DB 回查；`isAdmin` 嵌入令牌 payload |
| ORM | Spring Data JPA + Hibernate | 配合 `Specification` 动态查询；`ddl-auto=validate` 只校验 |
| Schema 迁移 | Flyway | 版本化迁移，与实体校验解耦 |
| 缓存 | Spring Data Redis（StringRedisTemplate） | 字符串透传 + JSON 序列化 |
| 文档 | Springdoc OpenAPI 3 | 各服务独立文档，`packages-to-scan` 隔离过渡期耦合 |
| 序列化 | Jackson + jackson-module-kotlin | Kotlin data class 友好（配置在 `common/src/main/kotlin/com/scx/backend/common/config/JacksonConfig.kt`） |

## 已知迁移遗留（阅读代码时注意）

- identity 的 `AuthInterceptor` / `AdminInterceptor` / common-web 的 `AccessLogInterceptor` 是 `@Component`，但注册它们的 `WebConfig` 已随 `app` 过渡单体删除（commit `271c543`），且 Spring Boot 不会自动注册 `HandlerInterceptor` Bean —— **这三个拦截器当前未挂载**，identity 端点级 `@Public` / `@Admin` 的强制执行实际依赖网关。详见 [authentication](../04-api/authentication.md) 的「下游安全配置对比」。
- `settings.gradle.kts` 注释中 file-service 仍标注「空壳」，实际 MinIO 文件能力已实装（以代码为准）。
- `TokenPayload` 在 `common/src/main/kotlin/com/scx/backend/common/security/TokenPayload.kt` 与 `identity-service/src/main/kotlin/com/scx/backend/identity/auth/AuthService.kt` 各定义了一份（历史重复）。

## 相关文档

- [gateway](../04-api/gateway.md) — 网关鉴权与路由细节
- [authentication](../04-api/authentication.md) — 令牌协议与下游安全配置
- [module-map](../03-codebase/module-map.md) — 各服务内部模块
- [repository-structure](../03-codebase/repository-structure.md) — 目录结构
