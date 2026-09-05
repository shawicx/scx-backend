# 统一响应与错误码

## 统一响应结构

所有接口（含异常）均返回统一的 `ApiResponse`（定义于 `common/src/main/kotlin/com/scx/backend/common/response/ApiResponse.kt`），字段顺序固定：

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

处理组件均在 **common-web** 模块（微服务拆分后从单体迁入，各 Servlet 服务自动生效）：

- 成功包装：`common-web/src/main/kotlin/com/scx/backend/common/response/GlobalResponseHandler.kt`（`ResponseBodyAdvice`）
- 异常处理：`common-web/src/main/kotlin/com/scx/backend/common/exception/GlobalExceptionHandler.kt`（`@RestControllerAdvice`）
- 网关（WebFlux）不走这两个组件，但 `AuthGlobalFilter` 手工构造同构的 401 错误体

**默认成功消息**：200/201「请求成功」、202「请求已接受」、204「操作成功」。

## 响应包装规则

`GlobalResponseHandler` 按以下顺序处理 Controller 返回值：

1. **已是 ApiResponse** → 原样返回（异常处理器或手动构造的，不重复包装）
2. **Map 含 `message` 键** → 提取 `message` 作为响应消息，剩余字段作为 `data`（无剩余则 `data=null`）
3. **其它**（DTO 等）→ 默认包装（`message` 取默认值，DTO 作为 `data`）

**不包装的路径**：`/api/v3/api-docs*`、`/api/swagger*`、`/api/webjars*`、`/api/actuator*`。

## 错误码体系

定义于 `common/src/main/kotlin/com/scx/backend/common/exception/SystemErrorCode.kt`，范围 `9000-9013` 共 14 个。（`9500-9509` AI 相关段不在本项目范围。）

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

`SystemErrorCode.mapToHttpStatus(errorCode)`：

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

> **设计要点**：9012/9008 返回 HTTP 200，因为它们是「请求被正确处理，但业务结果为失败」；`success=false` 与 `statusCode` 区分成败。网关侧 401（令牌缺失/无效/失效）也使用业务码 9000。

## 异常处理流程

`GlobalExceptionHandler` 处理：

1. **业务异常（SystemException）**：`SystemException` 伴生对象工厂方法构造，携带错误码与自定义消息
2. **参数校验异常（MethodArgumentNotValidException）**：提取 `@NotBlank` / `@Size` / `@Pattern` 等注解的 message → 400 + 9001（含 file-service 上传超限的场景）
3. **资源未找到（NoResourceFoundException）**：请求不存在的路由 → 404
4. **兜底（Exception）**：500，记完整堆栈，响应消息脱敏

## 使用示例

```kotlin
import com.scx.backend.common.exception.SystemException

// 抛出业务异常
throw SystemException.emailExists()                          // 预设消息
throw SystemException.dataNotFound("用户不存在")               // 自定义消息

// Controller 返回显式 DTO（由 GlobalResponseHandler 自动包装）
fun detail(): UserResponseDto = userService.findById(id)
fun delete(): MessageDto = MessageDto("删除成功")
```

## 相关文档

- [gateway](./gateway.md) — 网关 401 错误体构造
- [authentication](./authentication.md) — 鉴权错误码使用场景
- [development-guide](../08-development/development-guide.md) — 如何抛出业务异常、新增错误码
