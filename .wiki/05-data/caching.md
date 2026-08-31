# 缓存设计

## 概览

Redis 用于：单点令牌存储（access / refresh）、密码加密密钥（短期）、邮箱验证码（注册 / 登录）。

**使用方（微服务拆分后有两个）**：

| 服务 | 用途 | 客户端 |
| --- | --- | --- |
| identity-service | 令牌签发/删除、验证码、加密密钥、健康探针 | `StringRedisTemplate`（经 CacheService 封装） |
| gateway | 单点令牌校验（`access_token:{userId}` 比对） | `ReactiveStringRedisTemplate`（非阻塞） |

rbac / notification / file 不使用 Redis。

## CacheService API

封装于 `identity-service/src/main/kotlin/com/scx/backend/identity/cache/CacheService.kt`（微服务拆分后从单体 `modules/cache` 迁入 identity），`RedisConfig.kt` 同包声明 `StringRedisTemplate` Bean。

### 写入

```kotlin
cacheService.set("mykey", "myvalue", 3600L)          // String 原样，1 小时
cacheService.set("mykey", myObject, 3600L)            // 非 String 自动 JSON 序列化
cacheService.set("mykey", "myvalue", null)            // ttl null 或 <=0 → 永不过期

cacheService.setWithMilliseconds(                     // 毫秒级精确 TTL（令牌场景）
    CacheKeys.accessToken(userId), token, TtlConstants.ACCESS_TOKEN_TTL_MS)
```

### 读取

```kotlin
val token: String? = cacheService.get<String>(CacheKeys.accessToken(userId))
```

键不存在返回 `null`；值是合法 JSON 自动反序列化，不是 JSON（如纯字符串令牌）返回原始字符串。**注意**：纯数字验证码可能被解析为数字，使用时 `.toString()` 统一。

### 其他操作

| 方法 | 说明 |
| --- | --- |
| `del(key)` | 删除键 |
| `exists(key)` | 键是否存在 |
| `ttl(key)` | 剩余 TTL（秒），`-1` 永不过期，`-2` 不存在 |
| `flushAll()` | 清空当前 db（⚠️ 危险） |
| `testConnection()` | set/get/del 探针 |
| `getConnectionInfo()` | 连接信息 |
| `getRedisTemplate()` | 底层 `StringRedisTemplate`（高级操作） |

### 异常处理（fail-fast）

所有方法捕获异常后：记录 ERROR 日志（含 key 与堆栈）→ 抛 `RuntimeException`。Redis 不可用不会静默失败。

## Key 命名规范

工厂方法统一在 `common/src/main/kotlin/com/scx/backend/common/constants/CacheKeys.kt`（common 模块，网关与 identity 共用）：

| 方法 | Key 模板 | 用途 | 写入方 |
| --- | --- | --- | --- |
| `accessToken(userId)` | `access_token:{userId}` | 访问令牌（单点校验） | identity AuthService；gateway 读取比对 |
| `refreshToken(userId)` | `refresh_token:{userId}` | 刷新令牌（单点校验） | AuthService |
| `encryptionKey(keyId)` | `encryption_key:{keyId}` | 密码加密密钥 | AuthService |
| `emailVerification(email)` | `email_verification:{email}` | 注册验证码 | UserService |
| `loginVerification(email)` | `login_verification:{email}` | 登录验证码 | UserService |

命名约定：`{业务域}:{标识符}`，全小写下划线分词，工厂方法构造避免硬编码。

## TTL 常量

定义在 `common/src/main/kotlin/com/scx/backend/common/constants/TtlConstants.kt`，单位**毫秒**：

| 常量 | 值 | 时长 | 用途 |
| --- | --- | --- | --- |
| `ACCESS_TOKEN_TTL_MS` | 7,200,000 | 2 小时 | 访问令牌 |
| `REFRESH_TOKEN_TTL_MS` | 604,800,000 | 7 天 | 刷新令牌 |
| `ENCRYPTION_KEY_TTL_MS` | 300,000 | 5 分钟 | 密码加密密钥 |
| `EMAIL_VERIFICATION_TTL_MS` | 600,000 | 10 分钟 | 注册验证码 |
| `LOGIN_VERIFICATION_TTL_MS` | 600,000 | 10 分钟 | 登录验证码 |

## 单点令牌机制

令牌存储是缓存最核心的场景：

```
登录：  AuthService.generateAccessToken(userId, email, isAdmin)
        → 写 access_token:{userId}（2h）+ refresh_token:{userId}（7d）

校验：  gateway AuthGlobalFilter / identity AuthService
        → 验签 → Redis 读取 cachedToken → cachedToken == token（必须完全相等）

登出：  AuthService.logout(userId) → del 两个 key
```

效果：新设备登录覆盖旧令牌（立即失效）；刷新令牌重签新对。

## 健康探针

identity 的 `HealthService` 用 CacheService 做 Redis 探针：`setWithMilliseconds("health-check-test", "test", 5000)` → get → del，值相等即正常。详见 [module-map - health](../03-codebase/module-map.md#identity--health-健康检查)。

## Redis 连接与 db 隔离

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6388}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}   # 逻辑库编号
```

- 本地（docker-compose）：自建 redis:7-alpine，监听 6388，db 0
- 生产（ECS）：共享 **scx-infra** 的 scx-redis 实例，用 database 编号做多项目隔离——**backend=0 / service=1 / stock=2**；gateway 与 identity 必须配相同的 `REDIS_DATABASE`，否则网关读到空值会拒绝所有请求

## 相关文档

- [authentication](../04-api/authentication.md) — 令牌存储与单点机制
- [gateway](../04-api/gateway.md) — 网关侧的 Redis 校验
- [configuration](../06-configuration/configuration.md) — Redis 环境变量
