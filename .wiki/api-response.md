# 统一响应与错误码

## 统一响应结构

所有接口（含异常）均返回统一的 `ApiResponse` 结构，字段顺序固定：

```json
{
  "success": true,
  "statusCode": 200,
  "message": "请求成功",
  "data": { },
  "timestamp": "2026-07-19T08:00:00.000Z",
  "path": "/api/users/list"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | `true` 成功，`false` 失败 |
| `statusCode` | int | 成功为 HTTP 状态码（200/201/202/204），失败为业务错误码（9000+） |
| `message` | string | 提示消息 |
| `data` | any | 业务数据，失败时为 `null` |
| `timestamp` | string | ISO 8601 时间戳（`Instant.now()`） |
| `path` | string | 请求路径（`requestURI`） |

## 成功响应

成功响应由 `GlobalResponseHandler`（`ResponseBodyAdvice`）自动包装。

```json
{
  "success": true,
  "statusCode": 200,
  "message": "请求成功",
  "data": { "id": "01HXYZ...", "email": "user@example.com" },
  "timestamp": "2026-07-19T08:00:00.000Z",
  "path": "/api/users/register"
}
```

**默认消息**（按 HTTP 状态码）：

| HTTP 状态 | 默认 message |
| --- | --- |
| 200 / 201 | 请求成功 |
| 202 | 请求已接受 |
| 204 | 操作成功 |
| 其他 | 请求成功 |

## 失败响应

失败响应由 `GlobalExceptionHandler`（`@RestControllerAdvice`）构造，`success=false`，`statusCode` 为业务错误码（非 HTTP 码）。

```json
{
  "success": false,
  "statusCode": 9006,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": "2026-07-19T08:00:00.000Z",
  "path": "/api/users/login-password"
}
```

HTTP 状态码由错误码映射决定（见下文）。

## 响应包装规则

`GlobalResponseHandler` 对 Controller 返回值按以下顺序处理：

### 规则 1：已是 ApiResponse —— 原样返回

异常处理器或手动构造的 `ApiResponse`，不重复包装。

### 规则 2：Map 含 message 键 —— 提取消息

```kotlin
// Controller 返回
return mapOf("message" to "删除成功")
// 包装后
{ "success": true, "message": "删除成功", "data": null, ... }

// Controller 返回
return mapOf("message" to "成功", "count" to 5)
// 包装后
{ "success": true, "message": "成功", "data": { "count": 5 }, ... }
```

- 提取 `message` 作为响应消息
- 剩余字段作为 `data`（无剩余字段则 `data=null`）

### 规则 3：其它 —— 默认包装

```kotlin
// Controller 返回 DTO
return UserResponseDto(...)
// 包装后
{ "success": true, "message": "请求成功", "data": { ...dto }, ... }
```

### 不包装的路径

以下路径直接返回原始响应，**不**包装为 `ApiResponse`：

- `/api/v3/api-docs*` — OpenAPI JSON
- `/api/swagger*` — Swagger UI 资源
- `/api/webjars*` — 静态资源
- `/api/actuator*` — Actuator 端点

## 错误码体系

错误码定义在 `common/exception/SystemErrorCode.kt`，范围为 `9000-9013`。

> 注：`9500-9509` 段（AI 相关）不在本项目范围。

### 完整错误码表

| 枚举名 | code | 默认消息 | HTTP 状态 |
| --- | --- | --- | --- |
| `MISSING_TOKEN` | 9000 | 缺少访问令牌 | 401 |
| `INVALID_PARAMETER` | 9001 | 请求参数错误 | 400 |
| `DATA_NOT_FOUND` | 9002 | 数据未找到 | 404 |
| `INSUFFICIENT_PERMISSION` | 9003 | 权限不足 | 403 |
| `EMAIL_EXISTS` | 9004 | 该邮箱已被注册 | 409 |
| `INVALID_VERIFICATION_CODE` | 9005 | 验证码无效或已过期 | 400 |
| `INVALID_CREDENTIALS` | 9006 | 用户名或密码错误 | 401 |
| `RESOURCE_EXISTS` | 9007 | 资源已存在 | 409 |
| `OPERATION_FAILED` | 9008 | 操作失败 | 200 |
| `SERVICE_UNAVAILABLE` | 9009 | 服务暂时不可用 | 503 |
| `KEY_EXPIRED` | 9010 | 加密密钥已过期，请重新获取 | 401 |
| `DECRYPTION_FAILED` | 9011 | 数据解密失败 | 400 |
| `BUSINESS_RULE_VIOLATION` | 9012 | 业务规则限制 | 200 |
| `ACCOUNT_DISABLED` | 9013 | 账户已被禁用 | 401 |

### HTTP 状态码映射规则

`SystemErrorCode.mapToHttpStatus(errorCode)` 按以下规则映射：

| HTTP 状态 | 错误码 | 含义 |
| --- | --- | --- |
| **200** | 9012, 9008 | 业务规则违反 / 操作失败（业务层面失败，非协议错误） |
| **400** | 9001, 9005, 9011 | 参数错误 / 验证码无效 / 解密失败 |
| **401** | 9000, 9006, 9010, 9013 | 未认证 / 凭证无效 / 密钥过期 / 账户禁用 |
| **403** | 9003 | 权限不足 |
| **404** | 9002 | 数据未找到 |
| **409** | 9004, 9007 | 邮箱已存在 / 资源已存在 |
| **503** | 9009 | 服务不可用 |
| **500** | 其他 | 兜底：未处理的异常 |

> **设计要点**：业务规则违反（9012）和操作失败（9008）返回 HTTP 200，因为它们是"请求被正确处理，但业务结果为失败"，而非协议错误。`success=false` 与 `statusCode` 区分成功/失败。

## 异常处理流程

`GlobalExceptionHandler` 处理三类异常：

### 1. 业务异常（SystemException）

```kotlin
throw SystemException.emailExists()
// → 409 + { success: false, statusCode: 9004, message: "该邮箱已被注册" }
```

通过 `SystemException` 伴生对象的工厂方法构造，携带错误码与自定义消息。

### 2. 参数校验异常（MethodArgumentNotValidException）

```kotlin
// @Valid 校验失败
// → 400 + { success: false, statusCode: 9001, message: "<字段校验错误信息>" }
```

提取 `@NotBlank`、`@Size`、`@Pattern` 等注解的 `message` 作为响应消息。

### 3. 兜底异常（Exception）

```kotlin
// 未捕获的异常
// → 500 + { success: false, statusCode: 500, message: "服务器内部错误" }
```

记录完整堆栈到日志，响应消息脱敏（不暴露内部细节）。

### 4. 资源未找到（NoResourceFoundException）

```kotlin
// 请求不存在的路由
// → 404 + { success: false, statusCode: 404, message: "资源不存在" }
```

## 使用示例

### 抛出业务异常

```kotlin
import com.scx.backend.common.exception.SystemException

// 使用预设消息
throw SystemException.emailExists()

// 自定义消息
throw SystemException.invalidCredentials("邮箱或密码错误")
throw SystemException.dataNotFound("用户不存在")
throw SystemException.operationFailed("不能删除自己")
```

### Controller 返回

```kotlin
// 返回 DTO（自动包装）
@GetMapping("/detail")
fun detail(): UserResponseDto = userService.findById(id)

// 返回带消息的 Map
@DeleteMapping("/delete")
fun delete(): MessageDto = MessageDto("删除成功")

// 抛异常（由 GlobalExceptionHandler 处理）
fun delete(id: String) {
    if (notFound) throw SystemException.dataNotFound("角色不存在")
}
```

## 相关文档

- [架构概览](./architecture.md) — GlobalResponseHandler 在请求生命周期中的位置
- [认证与鉴权](./authentication.md) — 9000/9006/9010/9011 等鉴权错误码的使用场景
- [开发规范](./development-guide.md) — 如何正确抛出业务异常
