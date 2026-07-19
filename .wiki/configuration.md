# 配置说明

## Profile 机制

项目通过 Spring Profile 区分环境：

| Profile | 用途 | 默认端口 | Swagger |
| --- | --- | --- | --- |
| `dev` | 本地开发 | 3000 | 开启 |
| `prod` | 生产部署 | 由环境变量决定 | 关闭 |

**Profile 选择规则**：

- `SPRING_PROFILES_ACTIVE` 环境变量优先
- 未设置时，`bootRun` 默认 `dev`，`bootBuildImage` 默认 `prod`
- 配置项：`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`

Profile 特化文件：

- `application-dev.yml` — 开发环境覆盖配置
- `application-prod.yml` — 生产环境覆盖配置（如关闭 Swagger）

## 环境变量加载机制

项目通过 Spring Boot 原生机制加载 `.env` 文件：

```yaml
spring:
  config:
    import: "optional:file:.env[.properties]"
```

### 四级优先级

从高到低：

```
1. 真实 OS 环境变量          （最高优先级）
2. .env 文件                （项目根目录）
3. application.yml 默认值     （占位符 ${VAR:default}）
4. 无默认值则启动失败
```

### 四种启动方式一致性

| 启动方式 | 配置来源 |
| --- | --- |
| IDEA 运行 | 自动读 `.env` |
| `./gradlew bootRun` | 自动读 `.env` |
| `java -jar` | 自动读 `.env`（同目录） |
| Docker / K8s | 用 `-e` / Secret 注入（生产不用 `.env`） |

> Gradle daemon 不读取新 export 的环境变量，因此环境变量统一通过 `.env` 文件加载，四种启动方式行为完全一致。

## 完整配置项

配置定义在 `src/main/resources/application.yml`，所有值均通过 `${VAR:default}` 占位符引用环境变量。

### 应用基础（app）

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `NODE_ENV` | `app.env` | `development` | 运行环境标识 |
| `PORT` | `server.port` / `app.port` | `3000` | HTTP 端口 |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `dev` | Spring Profile |

### 数据库（PostgreSQL）

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DB_HOST` | `spring.datasource.url` | `localhost` | 数据库主机 |
| `DB_PORT` | `spring.datasource.url` | `5433` | 数据库端口 |
| `DB_DATABASE` | `spring.datasource.url` | `scx-backend` | 数据库名 |
| `DB_USERNAME` | `spring.datasource.username` | `scx` | 用户名 |
| `DB_PASSWORD` | `spring.datasource.password` | `scx_password` | 密码 |

JPA 配置：

- `spring.jpa.open-in-view=false`（关闭 OSIV，避免视图层延迟加载）
- `spring.jpa.hibernate.ddl-auto=validate`（仅校验 schema，不自动建表）
- `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`

### Flyway 迁移

| 配置项 | 值 | 说明 |
| --- | --- | --- |
| `spring.flyway.enabled` | `true` | 启用 Flyway |
| `spring.flyway.locations` | `classpath:db/migration` | 迁移文件目录 |
| `spring.flyway.baseline-on-migrate` | `true` | 已有库上首次迁移建立基线 |

详见 [数据库设计 - Flyway 规范](./database.md#flyway-迁移规范)。

### Redis

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `REDIS_HOST` | `spring.data.redis.host` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `spring.data.redis.port` | `6388` | Redis 端口 |
| `REDIS_PASSWORD` | `spring.data.redis.password` | （空） | Redis 密码 |

### 邮件（SMTP）

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MAIL_ENABLED` | `mail.enabled` | `true` | `false` 用 StubMailService（不发信） |
| `MAIL_HOST` | `spring.mail.host` | `smtp.example.com` | SMTP 主机 |
| `MAIL_PORT` | `spring.mail.port` | `587` | SMTP 端口 |
| `MAIL_USER` | `spring.mail.username` | `noreply@example.com` | SMTP 用户名 |
| `MAIL_PASSWORD` | `spring.mail.password` | **无默认值** | SMTP 授权码（必须注入） |
| `MAIL_SSL` | `spring.mail.properties.mail.smtp.ssl.enable` | `false` | 465 端口用 SSL |
| `MAIL_STARTTLS` | `spring.mail.properties.mail.smtp.starttls.enable` | `true` | 587 端口用 STARTTLS |
| `MAIL_TIMEOUT_MS` | `mail.timeout-ms` | `30000` | 发送超时（毫秒） |

**QQ 邮箱示例**（465 + SSL）：

```bash
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_SSL=true
MAIL_STARTTLS=false
MAIL_USER=your_qq@qq.com
MAIL_PASSWORD=your_smtp_auth_code   # 授权码，非登录密码
```

**Gmail 示例**（587 + STARTTLS）：

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_SSL=false
MAIL_STARTTLS=true
```

详见 [邮件服务](./mail.md)。

### JWT / 令牌

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `JWT_SECRET` | `jwt.secret` | `default-secret` | HMAC-SHA256 密钥 ⚠️ 生产必替换 |

详见 [认证与鉴权](./authentication.md)。

### 种子数据

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ADMIN_INITIAL_PASSWORD` | `admin.initial-password` | `changeme123` | 超管初始密码 ⚠️ 生产必替换 |

首次启动时 `SeedService` 创建超级管理员账号 `scx-super-admin@system.internal`，密码取此值。

### Swagger / OpenAPI

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `SWAGGER_ENABLED` | `swagger.enabled` | `true` | 是否启用（prod 默认 false） |
| `SWAGGER_TITLE` | `swagger.title` | `SCX Backend API` | 文档标题 |
| `SWAGGER_DESCRIPTION` | `swagger.description` | `SCX Backend API Documentation` | 文档描述 |
| `SWAGGER_VERSION` | `swagger.version` | `1.0` | 文档版本 |
| `SWAGGER_PATH` | `swagger.path` | `api/docs` | 文档路径 |

访问地址：

- Swagger UI：`/api/swagger-ui.html`
- OpenAPI JSON：`/api/v3/api-docs`

### Actuator / 健康检查

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info      # 仅暴露 health 和 info
  endpoint:
    health:
      show-details: always        # 显示健康详情
  health:
    livenessstate:
      enabled: false              # 关闭 K8s liveness 探针
    readinessstate:
      enabled: false              # 关闭 K8s readiness 探针
    mail:
      enabled: false              # 关闭 mail 健康检查（占位 SMTP）
```

访问地址：`/api/actuator/health`、`/api/actuator/info`

> 业务侧另有 `/api/health` 端点，返回结构化详情，与 Actuator 互补。详见 [业务模块 - health](./modules.md#health-健康检查)。

### 日志

```yaml
logging:
  level:
    root: INFO
    com.scx.backend: DEBUG        # 项目包开 DEBUG
```

日志配置文件：`src/main/resources/logback-spring.xml`，含访问日志、邮件模块独立日志等 Appender。

## 敏感配置清单

以下配置在生产环境**必须通过环境变量或 Secret 覆盖**，不得使用默认值：

| 配置 | 风险 | 建议 |
| --- | --- | --- |
| `JWT_SECRET` | 令牌可被伪造 | 强随机字符串（≥32 字节） |
| `ADMIN_INITIAL_PASSWORD` | 超管账号可被接管 | 强密码，首次登录后修改 |
| `MAIL_PASSWORD` | 邮件发送失败 / 被滥用 | SMTP 授权码，定期轮换 |
| `DB_PASSWORD` | 数据库可被未授权访问 | 强密码，限制网络访问 |

## 完整 `.env.example`

参考项目根目录的 [`.env.example`](../.env.example)，包含所有可配置项及注释。

## 相关文档

- [快速开始](./getting-started.md) — 配置完成后如何运行
- [邮件服务](./mail.md) — SMTP 详细配置
- [认证与鉴权](./authentication.md) — JWT 密钥用途
