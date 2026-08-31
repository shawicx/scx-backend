# 邮件服务

## 概览

邮件模块提供验证码、欢迎、密码重置、自定义 HTML 邮件发送能力，通过 `mail.enabled` 切换真实发送与 Stub 两种实现。

微服务拆分后独立为 **notification-service**（3003），代码位于 `notification-service/src/main/kotlin/com/scx/backend/notification/mail/`。过渡期 identity 进程内依赖本模块（Gradle 依赖 `project(":notification-service")`），`UserService` 直接调用 `MailService`。

## 架构

```
            MailService（接口）
               ▲       ▲
               │       │
    @Primary   │       │   @ConditionalOnProperty=false
               │       │
      SmtpMailService   StubMailService
      （真实发信）        （不发信，仅生成验证码）
```

| `mail.enabled` | 激活的实现 | 行为 |
| --- | --- | --- |
| `true`（默认） | `SmtpMailService`（`@Primary` + `matchIfMissing=true`） | JavaMailSender 真实发送，Thymeleaf 渲染 |
| `false` | `StubMailService` | 生成验证码但不发送，其余返回成功（测试/无 SMTP 环境） |

## 接口

`MailController`，前缀 `/api/mail`，全部 `@Public`（网关白名单 `/api/mail/**`）：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/mail/send-verification-code` | POST | 发送 6 位验证码 |
| `/mail/send-welcome-email` | POST | 发送欢迎邮件 |
| `/mail/send-password-reset` | POST | 发送密码重置邮件 |
| `/mail/send-html-email` | POST | 发送自定义 HTML 邮件 |

```bash
# 经网关
curl -X POST http://localhost:8080/api/mail/send-verification-code \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'
```

`send-password-reset` / `send-html-email` 发送失败抛 `SystemException.operationFailed()`（9008，HTTP 200）；验证码与欢迎邮件的失败由调用方决定处理方式。

## MailService 契约

```kotlin
interface MailService {
    fun sendVerificationCode(to: String): SendResult
    fun sendWelcomeEmail(to: String, username: String): SendResult
    fun sendPasswordResetEmail(to: String, resetToken: String, resetUrl: String): SendResult
    fun sendHtmlMail(to: String, subject: String, html: String): SendResult
    fun testConnection(): SendResult

    data class SendResult(
        val success: Boolean,
        val message: String,
        val code: String? = null,    // 验证码场景携带
        val error: String? = null,   // 失败时携带错误信息
    )
}
```

> `sendVerificationCode` 返回的 `SendResult.code` 携带生成的验证码，由调用方（identity 的 `UserService`）写入 Redis 供后续校验。Stub 实现同样生成验证码（`Random.nextInt(100000, 1000000)`），只是不发邮件。

## Thymeleaf 模板

位于 `notification-service/src/main/resources/templates/mail/`：

| 模板 | 变量 |
| --- | --- |
| `verification-code.html` | `code`、`expiryMinutes` |
| `welcome.html` | `username` |
| `password-reset.html` | `resetUrl`、`resetToken`、`expiryMinutes` |

开发环境 `spring.thymeleaf.cache: false`。

## SmtpMailService 实现细节

### 超时控制

`CompletableFuture.runAsync` + `future.get(timeoutMs)`，超时取消任务并抛 `RuntimeException("邮件发送超时 (…ms)")`。超时由 `mail.timeout-ms` 配置（默认 30000ms）。

### 错误分类（parseError）

| 异常特征 | 分类 | 说明 |
| --- | --- | --- |
| `Authentication failed` | 认证失败 | 用户名/授权码错误 |
| `Invalid Addresses` | 收件人无效 | 邮箱格式错误或不存在 |
| `Connect failed` / `timeout` | 连接失败 | 主机/端口/网络问题 |
| 其他 | 发送失败 | 兜底 |

## SMTP 配置

| 配置 | 说明 |
| --- | --- |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 服务器与端口 |
| `MAIL_USER` / `MAIL_PASSWORD` | 发件邮箱与**授权码**（⚠️ 非登录密码） |
| `MAIL_SSL=true`（465） | QQ/163 邮箱 |
| `MAIL_STARTTLS=true`（587） | Gmail 等 |

### 常见邮箱配置

```bash
# QQ 邮箱（465 + SSL）——授权码：设置 → 账户 → POP3/SMTP → 开启并生成
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_SSL=true
MAIL_STARTTLS=false

# Gmail（587 + STARTTLS，需应用专用密码）
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_SSL=false
MAIL_STARTTLS=true

# 163 邮箱（465 + SSL）
MAIL_HOST=smtp.163.com
MAIL_PORT=465
```

docker-compose 中 notification 默认 `MAIL_ENABLED=false`（Stub），避免本地起栈时占位 SMTP 报错。

## 测试

`notification-service/src/test/kotlin/com/scx/backend/notification/mail/MailModuleTest.kt`：`@SpringBootTest`（RANDOM_PORT）集成测试，覆盖 Thymeleaf 渲染与 Stub 路由（JDK HttpClient 打真实 HTTP）。

## 相关文档

- [module-map - mail](../03-codebase/module-map.md#notification--mail-邮件)
- [configuration](../06-configuration/configuration.md) — 完整 SMTP 环境变量
- [authentication](../04-api/authentication.md) — 验证码在登录流程中的使用
