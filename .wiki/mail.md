# 邮件服务

## 概览

邮件模块提供验证码、欢迎、密码重置、自定义 HTML 邮件发送能力，通过 `mail.enabled` 配置切换真实发送与 Stub 两种实现。

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

### 实现切换

通过 Spring 条件注解自动选择实现：

| `mail.enabled` | 激活的实现 | 行为 |
| --- | --- | --- |
| `true`（默认） | `SmtpMailService` | 通过 JavaMailSender 真实发送，Thymeleaf 渲染模板 |
| `false` | `StubMailService` | 生成验证码但不发送，其余返回成功（用于测试/无 SMTP 环境） |

- `SmtpMailService` 标注 `@Primary` + `@ConditionalOnProperty(havingValue = "true", matchIfMissing = true)`
- `StubMailService` 标注 `@ConditionalOnProperty(havingValue = "false")`

## 接口

所有接口位于 `MailController`，路由前缀 `/api/mail`，全部 `@Public`。

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/mail/send-verification-code` | POST | 发送 6 位验证码 |
| `/mail/send-welcome-email` | POST | 发送欢迎邮件 |
| `/mail/send-password-reset` | POST | 发送密码重置邮件 |
| `/mail/send-html-email` | POST | 发送自定义 HTML 邮件 |

### 请求示例

```bash
# 发送验证码
curl -X POST http://localhost:3000/api/mail/send-verification-code \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'

# 发送密码重置邮件
curl -X POST http://localhost:3000/api/mail/send-password-reset \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "resetToken": "xxx",
    "resetUrl": "https://app.example.com/reset?token=xxx"
  }'
```

### 发送失败处理

`send-password-reset` 和 `send-html-email` 在发送失败时抛出 `SystemException.operationFailed()`（业务码 9008，HTTP 200）。验证码和欢迎邮件的失败由调用方决定如何处理。

## MailService 接口

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

> **注意**：`sendVerificationCode` 返回的 `SendResult.code` 携带生成的验证码，由 `UserService` 写入 Redis 用于后续校验。Stub 实现也会生成验证码，只是不发送邮件。

## Thymeleaf 模板

邮件模板位于 `src/main/resources/templates/mail/`，使用 Thymeleaf 渲染。

| 模板文件 | 用途 | 变量 |
| --- | --- | --- |
| `verification-code.html` | 验证码邮件 | `code`、`expiryMinutes` |
| `welcome.html` | 欢迎邮件 | `username` |
| `password-reset.html` | 密码重置邮件 | `resetUrl`、`resetToken`、`expiryMinutes` |

模板渲染示例：

```kotlin
val context = Context().apply {
    setVariable("code", "123456")
    setVariable("expiryMinutes", 10)
}
val html = templateEngine.process("mail/verification-code", context)
```

Thymeleaf 配置（开发环境关闭缓存便于调试）：

```yaml
spring:
  thymeleaf:
    cache: false
```

## SmtpMailService 实现细节

### 超时控制

发送邮件通过 `CompletableFuture.orTimeout` 实现超时控制：

```kotlin
private fun sendWithTimeout(message: MimeMessage) {
    val future = CompletableFuture.runAsync { mailSender.send(message) }
    try {
        future.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        future.cancel(true)
        throw RuntimeException("邮件发送超时 (${timeoutMs}ms)")
    }
}
```

超时时间由 `mail.timeout-ms` 配置（默认 30000ms = 30 秒）。超时后取消任务并抛异常。

### 错误分类

`parseError(e)` 将 SMTP 异常分类，便于定位问题：

| 异常特征 | 分类 | 说明 |
| --- | --- | --- |
| `Authentication failed` | 认证失败 | 用户名/授权码错误 |
| `Invalid Addresses` | 收件人无效 | 邮箱格式错误或不存在 |
| `Connect failed` / `timeout` | 连接失败 | SMTP 主机/端口/网络问题 |
| 其他 | 发送失败 | 兜底 |

错误信息记录到日志并返回给调用方。

## SMTP 配置

### 配置项

详见 [配置说明 - 邮件](./configuration.md#邮件smtp)，关键项：

| 配置 | 说明 |
| --- | --- |
| `spring.mail.host` | SMTP 服务器 |
| `spring.mail.port` | 端口 |
| `spring.mail.username` | 发件邮箱 |
| `spring.mail.password` | SMTP 授权码（⚠️ 非登录密码） |
| `mail.smtp.ssl.enable` | 465 端口用 SSL |
| `mail.smtp.starttls.enable` | 587 端口用 STARTTLS |

### 常见邮箱配置

#### QQ 邮箱（465 + SSL）

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_SSL=true
MAIL_STARTTLS=false
MAIL_USER=your_qq@qq.com
MAIL_PASSWORD=your_smtp_auth_code
```

> **QQ 授权码获取**：邮箱设置 → 账户 → POP3/SMTP 服务 → 开启 → 生成授权码（**不是登录密码**）

#### Gmail（587 + STARTTLS）

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_SSL=false
MAIL_STARTTLS=true
MAIL_USER=your@gmail.com
MAIL_PASSWORD=your_app_password
```

> Gmail 需使用"应用专用密码"，非账户密码。

#### 163 邮箱（465 + SSL）

```bash
MAIL_HOST=smtp.163.com
MAIL_PORT=465
MAIL_SSL=true
MAIL_STARTTLS=false
```

## Stub 实现

`StubMailService` 用于测试和无 SMTP 环境：

```kotlin
override fun sendVerificationCode(to: String): SendResult {
    val code = Random.nextInt(100000, 1000000).toString()
    return SendResult(success = true, message = "验证码邮件发送成功（stub）", code = code)
}
```

- 生成真实的 6 位验证码（验证码校验流程可正常走通）
- 不发送真实邮件
- 其余方法返回成功消息

**适用场景**：

- 本地开发无 SMTP 配置
- 单元测试 / 集成测试
- CI 环境

## 关闭邮件健康检查

由于开发环境 SMTP 通常为占位配置（`smtp.example.com`），Actuator 的 mail 健康检查会失败。项目默认关闭：

```yaml
management:
  health:
    mail:
      enabled: false
```

配置真实 SMTP 后可按需开启。

## 相关文档

- [业务模块 - mail](./modules.md#mail-邮件) — 模块在整体架构中的位置
- [配置说明](./configuration.md) — 完整 SMTP 配置项
- [认证与鉴权](./authentication.md) — 验证码在登录流程中的使用
