# 配置说明

## 配置架构

微服务拆分后：**每个服务一份 `application.yml`**（共 5 份，无 `application-dev.yml` / `application-prod.yml` 分文件）+ **仓库根一份 `.env`**：

| 文件 | 内容 |
| --- | --- |
| `gateway/src/main/resources/application.yml` | 路由、CORS、Redis、jwt.secret |
| `identity-service/src/main/resources/application.yml` | 数据源、Redis、邮件、JWT、种子密码、Swagger（含 `packages-to-scan` 限定） |
| `rbac-service/src/main/resources/application.yml` | 数据源、Swagger |
| `notification-service/src/main/resources/application.yml` | 仅邮件 + Thymeleaf + Swagger（无数据源） |
| `file-service/src/main/resources/application.yml` | 数据源、MinIO、multipart 限制、Swagger |

## 环境变量加载机制

每个服务都配置：

```yaml
spring:
  config:
    import: "optional:file:.env[.properties]"
```

优先级（从高到低）：

```
1. 真实 OS 环境变量 / docker -e --env-file
2. .env 文件（相对进程工作目录）
3. application.yml 默认值（占位符 ${VAR:default}）
4. 无默认值且未注入 → 启动失败
```

> ⚠️ 实践注意：IDEA 运行主类（工作目录=仓库根）自动读根 `.env`；`./gradlew :<service>:bootRun` 的工作目录是**模块子目录**，读不到根 `.env`（详见 [local-development](../02-getting-started/local-development.md)）。容器环境通过 `--env-file` / `-e` 注入，不依赖 `.env`。

## Profile 机制

- `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`（默认 dev）
- `bootBuildImage` 构建的镜像统一注入 `SPRING_PROFILES_ACTIVE=prod`（根 `build.gradle.kts` 配置）
- 无 profile 特化文件，环境差异全部靠环境变量占位符

## 环境变量全表

完整模板见 [`.env.example`](../../.env.example)。按域分组：

### 通用 / 端口 / 服务地址

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `dev` | Spring Profile |
| `GATEWAY_PORT` | gateway `server.port` | `8080` | 网关（唯一对外） |
| `IDENTITY_PORT` / `RBAC_PORT` / `NOTIFICATION_PORT` / `FILE_PORT` | 各服务 `server.port` | `3001` / `3002` / `3003` / `3004` | 内部通信/本地调试 |
| `IDENTITY_BASE_URL` 等 4 个 | gateway 路由 URI | `http://localhost:300x` | 本地 localhost；容器/生产用服务名或 localhost（host 网络） |
| `NODE_ENV` | —（**无消费点**） | `development` | `.env.example` 模板遗留，代码已不读取 |

### 数据库（PostgreSQL）

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | 主机（容器环境为服务名/127.0.0.1） |
| `DB_PORT` | `5433` | 端口（compose 内容器同监听 5433） |
| `DB_DATABASE` | `scx-backend` | 库名（生产 scx-infra 单实例多库之一） |
| `DB_USERNAME` | `scx` | 用户名 |
| `DB_PASSWORD` | identity 默认 `scx_password`，模板建议覆盖 ⚠️ | 密码 |

JPA：`open-in-view=false`、`ddl-auto=validate`、`hibernate.jdbc.time_zone=UTC`；Flyway：`enabled=true`、`classpath:db/migration`、`baseline-on-migrate=true`（identity / rbac / file）。

### Redis

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | 主机 |
| `REDIS_PORT` | `6388` | 端口（compose 内同端口） |
| `REDIS_PASSWORD` | 空 | 密码 |
| `REDIS_DATABASE` | `0` | 逻辑库编号（生产 scx-infra 隔离：backend=0 / service=1 / stock=2）；gateway 与 identity 必须一致 |

### MinIO（file-service）

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MINIO_ENDPOINT` | `minio.endpoint` | `http://localhost:9000` | 服务连接地址（容器内用服务名） |
| `MINIO_PUBLIC_ENDPOINT` | `minio.public-endpoint` | `http://localhost:9000` | 浏览器可达地址（生成预签名 URL） |
| `MINIO_ACCESS_KEY` | `minio.access-key` | `scx_minio` | ⚠️ 生产必替换（与 MinIO root user 一致） |
| `MINIO_SECRET_KEY` | `minio.secret-key` | 模板无默认 ⚠️ | 与 MinIO root password 一致 |
| `MINIO_BUCKET` | `minio.bucket` | `scx-files` | 桶名（懒创建，保持私有） |
| `MINIO_PRESIGN_EXPIRY` | `minio.presign-expiry-seconds` | `3600` | 预签名 URL 有效期（MinIO 上限 7 天） |
| `FILE_MAX_FILE_SIZE` | `spring.servlet.multipart.max-file-size` | `50MB` | 单文件上限（超限 400） |
| `FILE_MAX_REQUEST_SIZE` | `spring.servlet.multipart.max-request-size` | `100MB` | 单请求上限 |

### JWT / 种子数据

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `JWT_SECRET` | `jwt.secret` | `default-secret` ⚠️ | HMAC-SHA256 密钥，**gateway 与 identity 必须一致** |
| `ADMIN_INITIAL_PASSWORD` | `admin.initial-password` | `changeme123` ⚠️ | 超管初始密码（SeedService） |

### 邮件（SMTP）

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MAIL_ENABLED` | `mail.enabled` | `true` | `false` 用 StubMailService（不发信） |
| `MAIL_HOST` / `MAIL_PORT` | `spring.mail.host/port` | `smtp.example.com` / `587` | SMTP |
| `MAIL_USER` / `MAIL_PASSWORD` | `spring.mail.username/password` | `noreply@example.com` / **无默认** | 授权码（非登录密码） |
| `MAIL_SSL` | `mail.smtp.ssl.enable` | `false` | 465 端口用 SSL |
| `MAIL_STARTTLS` | `mail.smtp.starttls.enable` | `true` | 587 端口用 STARTTLS |
| `MAIL_TIMEOUT_MS` | `mail.timeout-ms` | `30000` | 发送超时（毫秒） |

identity（过渡期进程内调用 MailService）与 notification 均需这些配置；docker-compose 中 notification 默认 `MAIL_ENABLED=false`。

### 应用与文档

| 环境变量 | 配置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `APP_NAME` | `app.name` | `SCX Service` | 邮件模板变量（`SmtpMailService` 读取） |
| `SWAGGER_ENABLED` | `swagger.enabled` | `true` | 各服务独立开关 |
| `SWAGGER_TITLE` / `SWAGGER_DESCRIPTION` / `SWAGGER_VERSION` | `swagger.*` | 各服务不同（如 `SCX Identity API`） | 文档标题等 |

访问地址（直连）：`http://localhost:300x/api/swagger-ui.html`、`/api/v3/api-docs`。identity 额外配置 `springdoc.packages-to-scan: com.scx.backend.identity` 限定文档范围。

## Actuator / 日志

- 暴露端点：各业务服务 `health,info`；gateway 额外暴露 `gateway`（路由排查）
- 业务健康检查 `/api/health`（identity）与 Actuator 互补
- 日志：root INFO、`com.scx.backend` DEBUG、gateway 对 `org.springframework.cloud.gateway` INFO

## 敏感配置清单

生产环境**必须**覆盖，不得使用默认值：

| 配置 | 风险 | 建议 |
| --- | --- | --- |
| `JWT_SECRET` | 令牌可伪造 | 强随机字符串（≥32 字节） |
| `ADMIN_INITIAL_PASSWORD` | 超管可被接管 | 强密码，首登后修改 |
| `DB_PASSWORD` | 数据库未授权访问 | 强密码，限制网络 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 对象存储被接管 | 强密钥对（与 MinIO root 凭据一致） |
| `MAIL_PASSWORD` | 邮件滥用 | SMTP 授权码，定期轮换 |

## 相关文档

- [local-development](../02-getting-started/local-development.md) — 配置完成后如何运行
- [mail](../07-integrations/mail.md) — SMTP 详细配置
- [deployment](../09-deployment/deployment.md) — 生产环境注入方式（ECS .env + --env-file）
