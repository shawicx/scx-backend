# SCX Backend

基于 **Kotlin + Spring Boot 4** 的微服务后端：API 网关统一入口 + 身份认证 / RBAC 权限 / 邮件通知 / 文件存储四个业务服务。

> 详细文档见 [`.wiki/`](./.wiki/README.md)（总入口：架构、模块地图、API/安全、数据、配置、部署）。

## 技术栈

| 分类          | 选型                                                                  |
|---------------|-----------------------------------------------------------------------|
| 语言 / 运行时 | Kotlin 2.2.20 / JDK 21                                               |
| 框架          | Spring Boot 4.0.7 + Spring Cloud 2025.1.2（Spring Cloud Gateway）     |
| 构建          | Gradle 9.3 (Kotlin DSL，仓库自带 `gradlew`)                          |
| 数据库        | PostgreSQL 16（Flyway 管理迁移，Hibernate 仅校验）                   |
| 缓存          | Redis 7（令牌单点校验 / 验证码）                                     |
| 对象存储      | MinIO（S3 兼容，SDK 9.0.3）                                          |
| 安全          | Spring Security + 自研 HMAC-SHA256 令牌（网关集中鉴权）              |
| 邮件          | JavaMail + Thymeleaf 模板（SMTP / Stub 双实现）                      |
| 文档          | Springdoc OpenAPI 3（各服务独立 Swagger UI）                         |
| 加密          | BouncyCastle（AES-256-CTR 密码登录加密）                             |

## 项目结构

```
scx-backend/
├── common/                # 跨服务协议库（令牌编解码/错误码/响应/常量/工具，无 Servlet）
├── common-web/            # Servlet 基础设施（全局异常/响应处理、身份解析、访问日志）
├── gateway/               # API 网关（8080，唯一对外入口：路由 + 集中鉴权 + CORS）
├── identity-service/      # 身份认证（3001：用户/登录/令牌/健康检查/种子数据）
├── rbac-service/          # 角色权限（3002：角色/树形权限/关联表）
├── notification-service/  # 邮件通知（3003，无数据库）
├── file-service/          # 文件存储（3004，MinIO）
├── docker-compose.yml       # 全栈自带基础设施编排（遗留；与本机 scx-infra 端口互斥）
├── docker-compose.local.yml # 本机容器直连 ECS 基础设施（仅 5 个应用服务）
└── .github/workflows/       # CI/CD（仅手动触发；原 push 自动部署版本备份于 deploy.yml.bak）
```

## 功能模块

- **用户模块**（identity）：邮箱验证码登录、密码登录（前端 AES 加密）、注册、登出、刷新令牌；用户增删改查、启停、批量删除（管理员）。
- **认证安全**：自研令牌协议（`base64(payload).hmacSha256`，payload 含 `isAdmin`），访问令牌 2h + 刷新令牌 7d，单点令牌（Redis 校验）；**鉴权集中在网关**，通过后注入 `X-User-*` 头给下游。
- **RBAC**（rbac）：用户 ↔ 角色 ↔ 权限三层模型；权限表自引用支持菜单树/按钮。
- **邮件模块**（notification）：`MAIL_ENABLED=false` 用 `StubMailService` 不真实发信；`true` 走 SMTP，支持验证码、欢迎、密码重置、自定义 HTML。
- **文件模块**（file）：MinIO 上传/批量上传/列表/详情/软删除，私有桶 + 预签名 URL 访问。
- **健康检查**：`/api/health`（业务侧）+ `/api/actuator/health`（Spring 原生）。
- **种子数据**：identity 启动时幂等创建 `SUPER_ADMIN` 角色与超级管理员账号。

## 快速开始

### 1. 准备环境

需要本地具备 JDK 21 与 Docker。

```bash
cd scx-backend
cp .env.example .env   # 按需修改（数据库、Redis、JWT、MinIO、邮件等）
```

### 2. 启动（按场景选择）

```bash
# 方式 A（日常开发，推荐）：本机 scx-infra 提供基础设施，服务宿主机跑（连本地库）
#   前置：cd scx-infra && docker compose up -d（PG:5433 / Redis:6388；MinIO 用 .env 里的 ECS 地址）
./gradlew :identity-service:bootRun   # 需要哪个跑哪个（Gateway/Rbac/Notification/File 同理）

# 方式 B（容器化，直连 ECS 生产基础设施 120.77.144.112:5433/6388）
./gradlew :gateway:bootBuildImage :identity-service:bootBuildImage \
  :rbac-service:bootBuildImage :notification-service:bootBuildImage \
  :file-service:bootBuildImage -x test
docker compose -f docker-compose.local.yml up -d

# 方式 C（遗留全栈）：自带 PG/Redis/MinIO 的 docker-compose.yml
#   需先停本机 scx-infra（端口互斥）；且其镜像 tag 写的 0.0.1-SNAPSHOT 与
#   Boot 4 构建产出的 latest 不符，需自行 retag，一般不再使用
```

方式 A/B 服务端口相同（8080/3001-3004），**不可同时启动**。每个服务启动成功后会打印环境摘要（连接的 DB/Redis/MinIO/下游地址），连远程基础设施时附 ⚠ 提示。

对外入口：`http://localhost:8080/api`（网关）。各服务需单独启动（`IdentityApplication` / `RbacApplication` / `NotificationApplication` / `FileApplication` / `GatewayApplication`）。

### 3. 验证与测试

```bash
curl http://localhost:8080/api/health    # 业务健康检查
./gradlew test                           # 运行测试
```

首次启动 identity 时自动创建超级管理员 `scx-super-admin@system.internal`（密码取 `ADMIN_INITIAL_PASSWORD`）。

## API 文档

各服务独立 Swagger UI（直连访问）：

- identity：`http://localhost:3001/api/swagger-ui.html`
- rbac：`http://localhost:3002/api/swagger-ui.html`
- notification：`http://localhost:3003/api/swagger-ui.html`
- file：`http://localhost:3004/api/swagger-ui.html`

网关路由：`/api/users/**`、`/api/health/**` → identity；`/api/roles/**`、`/api/permissions/**` → rbac；`/api/mail/**` → notification；`/api/files/**` → file。

## 统一响应格式

所有接口（含异常）均通过 `GlobalResponseHandler` / `GlobalExceptionHandler`（common-web 模块）返回：

```json
{
  "success": true,
  "statusCode": 200,
  "message": "...",
  "data": {},
  "timestamp": "2026-07-19T08:00:00Z",
  "path": "/api/users/list"
}
```

业务错误码范围 9000-9013，详见 [Wiki - conventions](./.wiki/04-api/conventions.md)。

## 鉴权机制

- **网关集中鉴权**：`AuthGlobalFilter` 白名单（`PublicPaths`）放行公开路由；其余验签（HMAC-SHA256）+ Redis 单点令牌比对，通过后注入 `X-User-Id` / `X-User-Email` / `X-User-Admin` 头转发。
- **下游信任头**：各服务从 `X-User-*` 头重建身份，不再各自解析令牌。
- **注解**：`@Public` 公开路由（登录、注册、健康检查、邮件等）；`@Admin` 需要管理员（角色 `SUPER_ADMIN` 或 code 以 `ADMIN` 开头）。
- **无状态会话**：不创建 `HttpSession`，关闭 CSRF。

## 配置说明

各服务一份 `application.yml` + 根目录 `.env`（`spring.config.import` 加载）。优先级：

**真实 OS 环境变量 > `.env` 文件 > `application.yml` 默认值**

完整变量见 [`.env.example`](./.env.example)，关键字段：

| 变量                                                                  | 说明                              | 默认                                                          |
|-----------------------------------------------------------------------|-----------------------------------|---------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`                                              | 运行 profile                      | `dev`                                                         |
| `GATEWAY_PORT` / `IDENTITY_PORT` / `RBAC_PORT` / `NOTIFICATION_PORT` / `FILE_PORT` | 各服务端口        | `8080` / `3001` / `3002` / `3003` / `3004`                   |
| `IDENTITY_BASE_URL` 等 `*_BASE_URL`                                   | 网关路由目标（容器/生产用服务名） | `http://localhost:300x`                                       |
| `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` / `DB_DATABASE` | PostgreSQL 连接                   | `localhost` / `5433` / `scx` / `scx_password` / `scx-backend` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE`     | Redis 连接（db 编号隔离）         | `localhost` / `6388` / 空 / `0`                               |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` 等         | MinIO 对象存储                    | `http://localhost:9000` / 见模板                              |
| `JWT_SECRET`                                                          | 令牌 HMAC 密钥（gateway 与 identity 必须一致） | `default-secret`                                 |
| `MAIL_ENABLED` / `MAIL_HOST` / `MAIL_PORT` / `MAIL_USER` / `MAIL_PASSWORD` | SMTP 配置                     | `true` / 占位值                                               |
| `ADMIN_INITIAL_PASSWORD`                                              | 超级管理员初始密码                | `changeme123`                                                 |

生产部署（push main 自动触发 GitHub Actions → 阿里云 ACR → ECS）详见 [Wiki - deployment](./.wiki/09-deployment/deployment.md)。

## 数据库

- 迁移文件 `V1__init_schema.sql` 位于 `rbac-service` 与 `file-service` 的 `src/main/resources/db/migration/`（内容相同；identity 经 classpath 依赖复用），启动时由 Flyway 自动执行。
- Hibernate `ddl-auto=validate`：仅校验实体与 schema 一致，不自动建表。
- 表结构与原 Prisma 迁移对齐（驼峰列名加双引号），主要表：`users` / `roles` / `permissions`（自引用树）/ `user_roles` / `role_permissions` / `files`。
