# 网关（Gateway）

网关是系统**唯一对外入口**（默认 8080），承担路由转发、集中鉴权与 CORS 统一处理。核心代码：

- `gateway/src/main/kotlin/com/scx/backend/gateway/GatewayApplication.kt` — 主类
- `gateway/src/main/kotlin/com/scx/backend/gateway/GatewayConfig.kt` — 注册 `TokenCodec` Bean（`jwt.secret` 与 identity 共享）
- `gateway/src/main/kotlin/com/scx/backend/gateway/AuthGlobalFilter.kt` — 集中鉴权过滤器
- `gateway/src/main/resources/application.yml` — 路由与 CORS

## 路由表

配置于 `gateway/src/main/resources/application.yml`。网关**不加 `/api` 前缀、原样透传**，下游服务用 `server.servlet.context-path: /api` 匹配：

| 路由 id | 断言（Path） | 目标 URI（环境变量） |
| --- | --- | --- |
| identity-service | `/api/users/**`、`/api/health`、`/api/health/**` | `${IDENTITY_BASE_URL:http://localhost:3001}` |
| rbac-service | `/api/roles/**`、`/api/permissions/**` | `${RBAC_BASE_URL:http://localhost:3002}` |
| notification-service | `/api/mail/**` | `${NOTIFICATION_BASE_URL:http://localhost:3003}` |
| file-service | `/api/files/**` | `${FILE_BASE_URL:http://localhost:3004}` |

> ⚠️ Spring Cloud Gateway 2025.1.x（SCG 5）的属性命名空间已从 `spring.cloud.gateway.*` 迁移为 **`spring.cloud.gateway.server.webflux.*`**，旧前缀会被静默忽略（路由不生效）。

## 集中鉴权流程（AuthGlobalFilter）

`GlobalFilter`，`Ordered.HIGHEST_PRECEDENCE`（最早执行），职责是把鉴权从下游服务收拢到网关：

```
请求进入
  │
  ├─ 1. 命中公开白名单（PublicPaths，Ant 匹配）
  │      → 清理客户端可能伪造的 X-User-* 头 → 直接放行
  │
  ├─ 2. 提取 Authorization: Bearer <token>     缺失 → 401「缺少访问令牌」
  │
  ├─ 3. TokenCodec 验签（HMAC-SHA256）并解析 payload
  │      拿到 userId / email / isAdmin（零 DB 访问）  失败 → 401「访问令牌无效」
  │
  ├─ 4. Redis 单点令牌校验（ReactiveStringRedisTemplate，非阻塞）
  │      access_token:{userId} 必须与请求令牌完全相等  不等 → 401「访问令牌已失效」
  │
  └─ 5. 通过 → 清理请求原有 X-User-* 头 → 注入受信任身份 → 转发下游
```

鉴权失败统一返回 **401 + ApiResponse 错误体（业务码 9000）**，与下游服务的错误结构一致。

## X-User-* 头协议

网关验证通过后注入的受信任请求头，下游服务据此重建身份（`common-web/src/main/kotlin/com/scx/backend/common/security/AuthContextResolver.kt`）：

| 头 | 值 | 来源 |
| --- | --- | --- |
| `X-User-Id` | 用户 ULID | 令牌 payload.userId |
| `X-User-Email` | 用户邮箱 | 令牌 payload.email |
| `X-User-Admin` | `true` / `false` | 令牌 payload.isAdmin |

**防伪造**：无论公开路径还是已认证路径，网关都先 `remove` 请求中原有的这三个头再决定注入与否——客户端自带的 `X-User-*` 头永远到不了下游。因此**直连下游端口绕过网关是本地调试专属行为**，生产上下游端口不对公网暴露。

## 公开白名单（PublicPaths）

定义在 `common/src/main/kotlin/com/scx/backend/common/security/PublicPaths.kt`，网关与下游共用：

```
/api/users/register          /api/users/login            /api/users/login-password
/api/users/encryption-key    /api/users/send-login-code  /api/users/send-email-code
/api/users/refresh-token
/api/mail/**
/api/health  /api/health/**
/api/swagger-ui/**  /api/v3/api-docs/**  /api/swagger-resources/**  /api/webjars/**
/api/actuator/**
```

## CORS 处理

- **全局 CORS**：`spring.cloud.gateway.server.webflux.globalcors`，`allowed-origin-patterns: "*"`、常用方法与 `Content-Type/Authorization` 头、`allow-credentials: true`
- **去重双头**：`default-filters: DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE`。原因：网关 globalcors 与下游 `SecurityConfig` 的 `CorsConfigurationSource` 各加一次 CORS 头，双头会被浏览器拒绝。

## 网关自身配置要点

| 配置 | 值 | 说明 |
| --- | --- | --- |
| `server.port` | `${GATEWAY_PORT:8080}` | 唯一对外端口 |
| `spring.data.redis.*` | host/port/password + `database: ${REDIS_DATABASE:0}` | 验单点令牌；生产 scx-infra Redis 用 db 编号隔离（backend=0 / service=1 / stock=2） |
| `jwt.secret` | `${JWT_SECRET:default-secret}` | **必须与 identity 一致**，否则验签失败 |
| `management.endpoints.web.exposure.include` | `health,info,gateway` | 多暴露 gateway 端点用于排查路由 |

技术栈：spring-cloud-starter-gateway-server-webflux + data-redis-reactive，排除 tomcat（Netty）。上下文装配有测试保障：`gateway/src/test/kotlin/com/scx/backend/gateway/GatewayContextTest.kt`。

## 请求示例

```bash
# 经网关（正常链路）：携带令牌
curl http://localhost:8080/api/users/list \
  -H "Authorization: Bearer <accessToken>"

# 无令牌访问受保护接口 → 401 + { "statusCode": 9000, ... }
curl http://localhost:8080/api/users/list

# 公开接口无需令牌
curl http://localhost:8080/api/health
```

## 相关文档

- [authentication](./authentication.md) — 令牌协议与下游安全配置
- [configuration](../06-configuration/configuration.md) — `*_BASE_URL` 与端口配置
- [deployment](../09-deployment/deployment.md) — 生产环境网关部署
