# 认证与鉴权

## 总览

项目采用**无状态认证** + **注解式鉴权**：

- **无状态**：不创建 `HttpSession`，关闭 CSRF，所有认证信息通过 Bearer 令牌传递
- **自研令牌协议**：非标准 JWT，使用 `base64(payload).hexHmac` 格式
- **单点令牌**：每个用户仅保留最新令牌，旧令牌立即失效
- **注解驱动**：`@Public` 放行、`@Admin` 要求管理员、其余默认需认证

## 令牌协议

### 格式

```
token = base64( JSON({userId, email, type, timestamp}) ) + "." + hexHmacSha256(payload, secret)
```

- **payload 部分**：JSON 字段顺序固定为 `userId, email, type, timestamp`（用 `LinkedHashMap` 保序）
- **base64**：标准编码（`Base64.getEncoder()`）
- **签名**：HMAC-SHA256，密钥来自 `jwt.secret`（环境变量 `JWT_SECRET`）
- **hex**：签名转为小写十六进制字符串

实现位于 `modules/auth/AuthService.kt`。

### 令牌类型与生命周期

| 类型 | type 字段 | 有效期 | Redis Key |
| --- | --- | --- | --- |
| Access Token | `access` | 2 小时 | `access_token:{userId}` |
| Refresh Token | `refresh` | 7 天 | `refresh_token:{userId}` |

TTL 常量定义在 `common/constants/TtlConstants.kt`。

### 单点令牌机制

每个用户的令牌同时写入 Redis，验证时需满足**两个条件**：

1. 签名有效（HMAC 校验通过）
2. Redis 中缓存的令牌与请求令牌**完全相等**

效果：

- 用户在新设备登录 → 旧令牌立即失效（Redis 被覆盖）
- 显式登出 → 删除 Redis 中的令牌
- 刷新令牌 → 同时签发新的 access + refresh，旧令牌失效

```
登录 → 签发 access+refresh，写入 Redis
  ↓
请求 → 校验签名 + Redis 比对
  ↓
再次登录 → 覆盖 Redis，旧令牌失效
  ↓
登出 → 删除 Redis 中的令牌
```

## 鉴权分层

请求经过三个阶段的安全检查，对应代码见 `security/` 包。

### 第一层：Spring Security Filter

`SecurityConfig.kt` 配置无状态安全策略：

```kotlin
csrf { disable() }                           // 关闭 CSRF（无状态 API）
sessionManagement { STATELESS }              // 不创建 HttpSession
authorizeHttpRequests {
    requestMatchers("/api/actuator/**").permitAll()       // Actuator 放行
    requestMatchers("/api/swagger-ui/**", ...).permitAll() // Swagger 放行
    anyRequest().permitAll()                               // 其余由 Interceptor 控制
}
addFilterBefore(TokenAuthenticationFilter, ...)            // 注册令牌解析过滤器
```

> 注意：Spring Security 层面所有请求都 `permitAll()`，真正的鉴权由 `AuthInterceptor` 基于 `@Public` 注解执行。这与"全局 Guard + 白名单"语义一致。

### 第二层：TokenAuthenticationFilter（令牌解析）

`TokenAuthenticationFilter.kt` 在 DispatcherServlet 之前执行：

- 从 `Authorization: Bearer <token>` 提取令牌
- 调用 `AuthService.validateAccessToken()` 校验
- 校验通过则将 `AuthPrincipal(userId, email)` 写入 `SecurityContext`
- **不强制鉴权**（无法获取 handler 注解），仅解析

### 第三层：HandlerInterceptor（鉴权强制）

三个拦截器按 order 执行：

| 顺序 | 拦截器 | order | 职责 |
| --- | --- | --- | --- |
| 1 | `AuthInterceptor` | HIGHEST_PRECEDENCE | `@Public` 放行，否则要求已认证 |
| 2 | `AdminInterceptor` | HIGHEST_PRECEDENCE + 1 | `@Admin` 校验管理员角色 |
| 3 | `AccessLogInterceptor` | 默认 | 记录访问日志 |

**鉴权失败统一返回 401**，响应体为 `ApiResponse`（业务码 `9000`，详见 [统一响应与错误码](./api-response.md)）。

## 鉴权注解

### @Public —— 公开路由

标注在 Controller 类或方法上，表示免鉴权。

```kotlin
@Public
@PostMapping("/login")
fun login(...): LoginResponseDto = ...
```

定义位置：`common/decorator/Public.kt`

**当前 `@Public` 接口**：

- 用户：注册、两种登录、获取加密密钥、发送验证码
- 邮件：所有发送接口
- 健康检查：`/api/health`

### @Admin —— 管理员路由

标注在 Controller 方法或类上，表示需要管理员权限。

```kotlin
@Admin
@GetMapping("/list")
fun queryUsers(...): UserListResponseDto = ...
```

定义位置：`security/Admin.kt`

**管理员判定规则**（`UserService.isAdmin`）：

- 拥有 `SUPER_ADMIN` 角色 → 是管理员
- 拥有 code 以 `ADMIN` 开头的角色 → 是管理员
- 其余 → 非管理员，返回 401（"需要管理员权限"）

**当前 `@Admin` 接口**（均在 UserController）：

- 用户列表查询、创建用户、批量删除、切换状态

## 登录流程

### 邮箱验证码登录

```
1. POST /api/users/send-login-code { email }
   → 校验用户存在 → 发送验证码 → 存入 Redis（login_verification:{email}，10 分钟）
2. POST /api/users/login { email, emailVerificationCode }
   → 校验用户存在且启用
   → 校验验证码（Redis 比对，成功后删除）
   → 更新登录信息（IP、时间、次数）
   → 签发 access + refresh 令牌
   → 返回 LoginResponseDto
```

### 密码登录（前端加密）

密码登录要求前端先用 AES 加密密码，防止明文传输：

```
1. GET /api/users/encryption-key
   → 生成 32 字节随机密钥（hex）
   → 生成 keyId（ULID）
   → 存入 Redis（encryption_key:{keyId}，5 分钟）
   → 返回 { key, keyId }

2. 前端用 key 通过 AES-256-CTR 加密密码，得到 encryptedPassword

3. POST /api/users/login-password { email, password: encryptedPassword, keyId }
   → 从 Redis 取密钥（不存在 → 9010 KEY_EXPIRED）
   → AES 解密得到明文密码（失败 → 9011 DECRYPTION_FAILED）
   → 校验用户存在且启用
   → BCrypt 比对密码（不符 → 9006 INVALID_CREDENTIALS）
   → 签发 access + refresh 令牌
```

**加密格式**：`ivHex:encryptedHex`（AES-256-CTR），由 `CryptoUtil` 实现，与前端加密算法互通。

### 登出

```
POST /api/users/logout?userId=xxx
→ 删除 Redis 中的 access + refresh 令牌
→ 旧令牌立即失效
```

### 刷新令牌

```
POST /api/users/refresh-token { refreshToken }
→ 校验 refresh 令牌（签名 + Redis 比对 + type=refresh）
→ 失败返回 9001 INVALID_PARAMETER
→ 成功则签发新的 access + refresh，旧令牌失效
```

## 密码存储

- 使用 `BCryptPasswordEncoder(strength = 12)` 加密
- 明文密码永不存储、永不返回（`UserResponseDto` 不含 password 字段）
- 注册时加密、密码登录时用 `matches()` 比对

## 相关文档

- [架构概览](./architecture.md) — Filter / Interceptor 链在请求生命周期中的位置
- [业务模块 - auth](./modules.md#auth-认证) — AuthService 详细说明
- [缓存设计](./caching.md) — 令牌存储的 Key 与 TTL
- [统一响应与错误码](./api-response.md) — 9000/9006/9010/9011 等鉴权错误码
