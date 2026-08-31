# 认证与鉴权

## 总览

微服务化后鉴权**集中在网关**，整体模型：

- **网关集中鉴权**：`AuthGlobalFilter` 验签 + Redis 单点令牌比对，通过后注入 `X-User-*` 头（详见 [gateway](./gateway.md)）
- **下游信任头**：各服务从 `X-User-*` 头重建身份（`AuthContextResolver`），不再各自解析令牌
- **无状态**：不创建 `HttpSession`，关闭 CSRF
- **自研令牌协议**：非标准 JWT，`base64(payload).hexHmac` 格式
- **单点令牌**：每用户仅最新令牌有效，旧令牌立即失效
- **注解驱动**：`@Public` 放行、`@Admin` 要求管理员（定义于 `common/src/main/kotlin/com/scx/backend/common/decorator/Public.kt` 与 `common/src/main/kotlin/com/scx/backend/common/security/Admin.kt`）

## 令牌协议

### 格式

```
token = base64( JSON({userId, email, type, timestamp, isAdmin}) ) + "." + hexHmacSha256(payload, secret)
```

- **payload 字段顺序固定**：`userId, email, type, timestamp, isAdmin`（`LinkedHashMap` 保序）
- **base64**：标准编码（`Base64.getEncoder()`）
- **签名**：HMAC-SHA256，密钥来自 `jwt.secret`（环境变量 `JWT_SECRET`，**gateway 与 identity 必须一致**）
- **hex**：小写十六进制
- **isAdmin**：微服务迁移新增字段，嵌入令牌使网关/拦截器免回查 DB 判定管理员；旧令牌缺失该字段时默认 `false`（向后兼容）

两处实现共用协议：签发在 `identity-service/src/main/kotlin/com/scx/backend/identity/auth/AuthService.kt`（`createToken`），编解码工具在 `common/src/main/kotlin/com/scx/backend/common/security/TokenCodec.kt`（网关验签用，由 `gateway/src/main/kotlin/com/scx/backend/gateway/GatewayConfig.kt` 注册为 Bean）。

### 令牌类型与生命周期

| 类型 | type 字段 | 有效期 | Redis Key |
| --- | --- | --- | --- |
| Access Token | `access` | 2 小时 | `access_token:{userId}` |
| Refresh Token | `refresh` | 7 天 | `refresh_token:{userId}` |

TTL 常量：`common/src/main/kotlin/com/scx/backend/common/constants/TtlConstants.kt`。

### 单点令牌机制

验证需同时满足：① 签名有效；② Redis 中缓存的令牌与请求令牌**完全相等**。效果：

- 新设备登录 → 覆盖 Redis，旧令牌立即失效
- 显式登出 → 删除 Redis 令牌
- 刷新令牌 → 签发新令牌对（旧令牌随之失效），并**重算 isAdmin**（角色变更后刷新令牌即生效）

## 登录流程

### 邮箱验证码登录

```
1. POST /api/users/send-login-code { email }
   → 校验用户存在 → 发送验证码 → Redis login_verification:{email}（10 分钟）
2. POST /api/users/login { email, emailVerificationCode }
   → 校验用户存在且启用 → 验证码 Redis 比对（成功后删除）
   → 更新登录 IP/时间/次数 → 签发 access + refresh（isAdmin 由 UserService 计算）
```

### 密码登录（前端加密）

```
1. GET /api/users/encryption-key
   → 32 字节随机 hex 密钥 + ULID keyId → Redis encryption_key:{keyId}（5 分钟）
2. 前端用 key 以 AES-256-CTR 加密密码（格式 ivHex:encryptedHex，CryptoUtil 实现）
3. POST /api/users/login-password { email, password, keyId }
   → 密钥不存在 → 9010 KEY_EXPIRED
   → 解密失败 → 9011 DECRYPTION_FAILED
   → BCrypt 比对不符 → 9006 INVALID_CREDENTIALS（不区分用户不存在与密码错误）
   → 通过 → 签发令牌对
```

### 登出 / 刷新

```
POST /api/users/logout?userId=xxx        → 删除 Redis 中的 access + refresh
POST /api/users/refresh-token { refreshToken }
   → 校验（签名 + type=refresh + Redis 比对），失败 → 9001
   → 成功 → 重算 isAdmin → 签发新令牌对
```

## 下游安全配置对比

各服务安全模型不同（以代码为准）：

| 服务 | 配置位置 | 机制 | 说明 |
| --- | --- | --- | --- |
| identity | `identity-service/src/main/kotlin/com/scx/backend/identity/security/SecurityConfig.kt` | `anyRequest().permitAll()` + `addFilterBefore(TokenAuthenticationFilter)` | Filter 解析身份（**X-User-* 头优先，本地 Bearer 兜底**）写 SecurityContext，仅解析不强制 |
| rbac | `rbac-service/src/main/kotlin/com/scx/backend/rbac/security/RbacSecurityConfig.kt` | `anyRequest().permitAll()` + `HeaderAuthFilter`（`@ConditionalOnMissingBean`） | 完全信任网关头，无本地令牌解析 |
| file | `file-service/src/main/kotlin/com/scx/backend/file/security/FileSecurityConfig.kt` | 同 rbac | 同上 |
| notification | （无 Spring Security 依赖） | 接口全部 `@Public` | 无安全配置 |

⚠️ **已知迁移遗留（待确认）**：identity 的 `AuthInterceptor`（`@Public` 检测 + 身份存在性 401）、`AdminInterceptor`（`@Admin` 校验，X-User-Admin/令牌 isAdmin 优先、DB 回查兜底）、common-web 的 `AccessLogInterceptor` 均为 `@Component`，但注册它们的 `WebConfig`（曾位于 `app` 过渡单体）已随 commit `271c543` 删除，且 Spring Boot 4 不会自动注册 `HandlerInterceptor` Bean —— **三者当前未挂载到 MVC 拦截器链**。实际效果：identity 端点级 `@Public` / `@Admin` 强制依赖网关；直连 3001 端口时除 SecurityConfig 放行外无端点级鉴权。若需恢复纵深防御，需在某服务补充 `WebMvcConfigurer.addInterceptors` 注册（与旧单体 `WebConfig` 等价）。

## 鉴权注解

### @Public（`common/src/main/kotlin/com/scx/backend/common/decorator/Public.kt`）

当前公开接口：注册、两种登录、获取加密密钥、发送验证码×2、刷新令牌（网关白名单同步放行）；邮件全部发送接口；`/api/health`。

### @Admin（`common/src/main/kotlin/com/scx/backend/common/security/Admin.kt`）

当前 `@Admin` 接口（均在 `UserController`）：用户列表、创建用户、批量删除、切换状态。

管理员判定（`UserService.isAdmin`，`identity-service/src/main/kotlin/com/scx/backend/identity/user/UserService.kt:308`）：

- `SUPER_ADMIN` 角色 → 管理员
- 角色 code 以 `ADMIN` 开头 → 管理员

## 密码存储

- `BCryptPasswordEncoder(strength = 12)` 加密
- 明文密码永不存储、永不返回（`UserResponseDto` 不含 password）

鉴权失败统一 401 + 业务码 9000，错误码全表见 [conventions](./conventions.md)。

## 相关文档

- [gateway](./gateway.md) — 网关鉴权过滤器与 X-User-* 头协议
- [caching](../05-data/caching.md) — 令牌存储的 Key 与 TTL
- [conventions](./conventions.md) — 9000/9006/9010/9011 等错误码
