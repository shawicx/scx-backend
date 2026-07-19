# 缓存设计

## 概览

项目使用 Redis 作为缓存层，封装在 `modules/cache/CacheService.kt`。主要用于：

- 单点令牌存储（access / refresh token）
- 密码加密密钥（短期）
- 邮箱验证码（注册 / 登录）

## CacheService API

`CacheService` 基于 `StringRedisTemplate` 封装，提供统一的序列化与异常处理。

### 写入

#### `set(key, value, ttlSeconds)` — 秒级 TTL

```kotlin
cacheService.set("mykey", "myvalue", 3600L)          // 1 小时
cacheService.set("mykey", myObject, 3600L)            // 对象自动 JSON 序列化
cacheService.set("mykey", "myvalue", null)            // 永不过期
```

- `value` 为 `String` → 原样存储
- `value` 为其他类型 → JSON 序列化后存储
- `ttlSeconds` 为 `null` 或 `<=0` → 永不过期

#### `setWithMilliseconds(key, value, ttlMilliseconds)` — 毫秒级 TTL

```kotlin
cacheService.setWithMilliseconds(CacheKeys.accessToken(userId), token, TtlConstants.ACCESS_TOKEN_TTL_MS)
```

用于需要精确毫秒控制的场景（如令牌 TTL）。

### 读取

#### `get<T>(key)` — 自动 JSON 解析

```kotlin
val token: String? = cacheService.get<String>(CacheKeys.accessToken(userId))
val user: User? = cacheService.get<User>("user:xxx")
```

- 键不存在 → 返回 `null`
- 值是合法 JSON → 自动反序列化为对象
- 值不是 JSON（如纯字符串令牌）→ 返回原始字符串

> **注意**：验证码场景下，纯数字验证码可能被解析为数字，使用时需 `.toString()` 统一处理。

### 其他操作

| 方法 | 说明 |
| --- | --- |
| `del(key)` | 删除键 |
| `exists(key)` | 检查键是否存在 |
| `ttl(key)` | 返回剩余 TTL（秒），`-1` 永不过期，`-2` 键不存在 |
| `flushAll()` | 清空当前数据库（⚠️ 危险操作） |
| `testConnection()` | 测试连接（set/get/del 探针） |
| `getConnectionInfo()` | 获取连接信息 |
| `getRedisTemplate()` | 获取底层 `StringRedisTemplate`（高级操作） |

### 异常处理

所有方法捕获异常并：

1. 记录 ERROR 日志（含 key 与异常堆栈）
2. 抛出 `RuntimeException`（消息含 Redis 操作类型与原始异常信息）

> Redis 不可用不会静默失败，会向上抛出导致请求失败（fail-fast 策略）。

## Key 命名规范

所有 Key 通过 `common/constants/CacheKeys.kt` 的工厂方法生成，统一管理。

### Key 一览

| 方法 | Key 模板 | 用途 | 写入方 |
| --- | --- | --- | --- |
| `accessToken(userId)` | `access_token:{userId}` | 访问令牌（单点校验） | AuthService |
| `refreshToken(userId)` | `refresh_token:{userId}` | 刷新令牌（单点校验） | AuthService |
| `encryptionKey(keyId)` | `encryption_key:{keyId}` | 密码加密密钥 | AuthService |
| `emailVerification(email)` | `email_verification:{email}` | 注册验证码 | UserService |
| `loginVerification(email)` | `login_verification:{email}` | 登录验证码 | UserService |

### 命名约定

- 格式：`{业务域}:{标识符}`
- 全小写，下划线分词
- 通过工厂方法构造，避免硬编码

## TTL 常量

所有 TTL 定义在 `common/constants/TtlConstants.kt`，单位为**毫秒**。

| 常量 | 值 | 时长 | 用途 |
| --- | --- | --- | --- |
| `ACCESS_TOKEN_TTL_MS` | 7,200,000 | 2 小时 | 访问令牌有效期 |
| `REFRESH_TOKEN_TTL_MS` | 604,800,000 | 7 天 | 刷新令牌有效期 |
| `ENCRYPTION_KEY_TTL_MS` | 300,000 | 5 分钟 | 密码加密密钥有效期 |
| `EMAIL_VERIFICATION_TTL_MS` | 600,000 | 10 分钟 | 注册验证码有效期 |
| `LOGIN_VERIFICATION_TTL_MS` | 600,000 | 10 分钟 | 登录验证码有效期 |

## 序列化策略

```
写入：
  String 值  ──────────────────► Redis（原样）
  其他类型   ──► JSON 序列化 ──► Redis（JSON 字符串）

读取：
  Redis 值 ──► 尝试 JSON.parse ──► 成功 ──► 返回对象
                              └─► 失败 ──► 返回原始字符串
```

**设计理由**：

- 令牌是纯字符串，原样存取避免 JSON 转义
- 验证码是纯数字字符串，原样存储
- 复杂对象（如未来缓存用户信息）自动 JSON 序列化

## 单点令牌机制

令牌存储是缓存最核心的应用场景，实现单点登录控制：

```
登录：
  AuthService.generateAccessToken(userId, email)
    → 生成 token
    → cacheService.setWithMilliseconds("access_token:{userId}", token, 2h)

校验：
  AuthService.validateAccessToken(token)
    → 校验签名
    → 读取 Redis: cachedToken = get("access_token:{userId}")
    → return cachedToken == token  （必须完全相等）

登出：
  AuthService.logout(userId)
    → del("access_token:{userId}")
    → del("refresh_token:{userId}")
```

**效果**：用户在新设备登录会覆盖 Redis 中的旧令牌，旧令牌立即失效。

## 健康检查

`HealthService` 使用 `CacheService` 做 Redis 探针：

```kotlin
// set + get + del 探针
cacheService.setWithMilliseconds("health-check-test", "test", 5000)
val value = cacheService.get<String>("health-check-test")
cacheService.del("health-check-test")
// value == "test" → Redis 正常
```

详见 [业务模块 - health](./modules.md#health-健康检查)。

## 配置

Redis 连接配置（详见 [配置说明](./configuration.md)）：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6388}
      password: ${REDIS_PASSWORD:}
```

`RedisConfig.kt` 显式声明 `StringRedisTemplate` Bean，便于后续扩展（自定义序列化、连接池等）。

## 相关文档

- [认证与鉴权](./authentication.md) — 令牌存储与单点令牌机制
- [业务模块 - cache](./modules.md#cache-缓存) — CacheService 在模块中的定位
- [配置说明](./configuration.md) — Redis 连接配置
