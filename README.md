# SCX Backend

基于 **Kotlin + Spring Boot 4** 的后端服务，提供用户、RBAC 权限、邮件、文件等核心能力。

## 技术栈

| 分类          | 选型                                               |
|---------------|----------------------------------------------------|
| 语言 / 运行时 | Kotlin 2.2 / JDK 21                                |
| 框架          | Spring Boot 4.0.7                                  |
| 构建          | Gradle (Kotlin DSL)                                |
| 数据库        | PostgreSQL 16（Flyway 管理迁移，Hibernate 仅校验） |
| 缓存          | Redis 7                                            |
| 安全          | Spring Security + 自研 HMAC-SHA256 令牌            |
| 邮件          | JavaMail + Thymeleaf 模板                          |
| 文档          | Springdoc OpenAPI 3 (Swagger UI)                   |
| 加密          | BouncyCastle（AES/HMAC 工具预留）                  |

## 项目结构

```
src/main/kotlin/com/scx/backend/
├── ScxBackendApplication.kt       # 启动入口
├── common/                        # 通用基础设施
│   ├── constants/                 # CacheKeys / TtlConstants
│   ├── decorator/                 # @Public 注解
│   ├── exception/                 # SystemException + 全局异常处理
│   ├── response/                  # ApiResponse 统一响应封装
│   ├── util/                      # IdGenerator / IpUtils / CryptoUtil
│   └── web/                       # AccessLogInterceptor 访问日志
├── config/                        # Jackson / OpenApi / Web / AppProperties
├── entity/                        # JPA 实体
├── repository/                    # JPA Repository
├── security/                      # SecurityConfig / TokenFilter / Auth & Admin 拦截器
└── modules/                       # 业务模块（按领域划分）
    ├── auth/                      # 自研令牌服务
    ├── user/                      # 用户与登录
    ├── role/                      # 角色
    ├── permission/                # 权限（树形菜单 + 按钮）
    ├── rolepermission/            # 角色-权限关联
    ├── userrole/                  # 用户-角色关联
    ├── mail/                      # 邮件（SMTP / Stub 双实现）
    ├── file/                      # 文件（部分 stub）
    ├── cache/                     # Redis 封装
    ├── health/                    # 业务健康检查
    └── seed/                      # 启动时种子数据
```

## 功能模块

- **用户模块**：邮箱验证码登录、密码登录、注册、登出、刷新令牌；用户增删改查、启停、批量删除（管理员）。
- **认证模块**：自研令牌协议（`base64(payload).hmacSha256`），访问令牌 2h + 刷新令牌 7d，单点令牌（Redis 校验）。
- **RBAC**：用户 ↔ 角色 ↔ 权限三层模型；权限表自引用支持菜单树/按钮。
- **邮件模块**：`MAIL_ENABLED=false` 时使用 `StubMailService` 不真实发信；`true` 时走 SMTP，支持验证码、欢迎、密码重置、自定义
  HTML。
- **健康检查**：`/api/health`（业务侧）+ `/api/actuator/health`（Spring 原生）。
- **种子数据**：应用启动时幂等创建 `SUPER_ADMIN` 角色与超级管理员账号。

## 快速开始

### 1. 准备环境

需要本地具备 JDK 21 与 Docker。

```bash
# 克隆后进入项目
cd scx-backend

# 复制环境变量模板并按需修改（数据库、Redis、JWT、邮件等）
cp .env.example .env
```

### 2. 启动依赖（PostgreSQL + Redis）

端口：PG `5433` / Redis `6388`。

```bash
docker compose up -d
```

### 3. 运行应用

```bash
# 默认 dev profile（读取项目根目录 .env）
./gradlew bootRun
```

应用监听 `http://localhost:3000`，全局路由前缀 `/api`。

### 4. 运行测试

```bash
./gradlew test
```

### 5. 构建

```bash
./gradlew build          # 生成 jar
./gradlew bootBuildImage # 构建容器镜像，默认 prod profile
```

## API 文档

启动后访问：

- Swagger UI：`http://localhost:3000/api/swagger-ui.html`
- OpenAPI JSON：`http://localhost:3000/api/v3/api-docs`

生产环境（`prod` profile）默认关闭 Swagger。

## 统一响应格式

所有接口（含异常）均通过 `GlobalResponseHandler` / `GlobalExceptionHandler` 返回：

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

## 鉴权机制

- **无状态会话**：不创建 `HttpSession`，关闭 CSRF。
- **令牌解析**：`TokenAuthenticationFilter` 从 `Authorization: Bearer <token>` 解析用户信息注入 `SecurityContext`。
- **路由鉴权**：
  - `@Public` —— 公开路由，免鉴权（登录、注册、健康检查、邮件等）。
  - 默认 —— 需要有效访问令牌。
  - `@Admin` —— 需要管理员角色（由 `AdminInterceptor` 强制校验）。
- **单点令牌**：Redis 缓存的令牌必须与请求令牌完全相等，否则视为失效。

## 配置说明

### Profile

| Profile | 用途                              | 默认端口 / Swagger |
|---------|-----------------------------------|--------------------|
| `dev`   | 本地开发（`bootRun` 默认）        | 3000 / 开启        |
| `prod`  | 生产部署（`bootBuildImage` 默认） | 由环境变量 / 关闭  |

通过 `SPRING_PROFILES_ACTIVE` 切换。

### 环境变量加载顺序

四种启动方式（IDEA / `bootRun` / `java -jar` / Docker）一致，优先级：

**真实 OS 环境变量 > `.env` 文件 > `application.yml` 默认值**

完整变量见 [`.env.example`](./.env.example)，关键字段：

| 变量                                                                  | 说明                           | 默认                                                          |
|-----------------------------------------------------------------------|--------------------------------|---------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`                                              | 运行 profile                   | `dev`                                                         |
| `PORT`                                                                | 服务端口                       | `3000`                                                        |
| `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` / `DB_DATABASE` | PostgreSQL 连接                | `localhost` / `5433` / `scx` / `scx_password` / `scx-backend` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`                        | Redis 连接                     | `localhost` / `6388` / 空                                     |
| `JWT_SECRET`                                                          | 令牌 HMAC 密钥（生产必须替换） | `default-secret`                                              |
| `MAIL_ENABLED`                                                        | 是否真实发信                   | `true`                                                        |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USER` / `MAIL_PASSWORD`             | SMTP 配置                      | 占位值                                                        |
| `MAIL_SSL` / `MAIL_STARTTLS`                                          | 465 SSL 或 587 STARTTLS        | `false` / `true`                                              |
| `ADMIN_INITIAL_PASSWORD`                                              | 超级管理员初始密码             | `changeme123`                                                 |
| `SWAGGER_*`                                                           | 文档标题、版本、路径           | 见模板                                                        |

## 数据库

- 迁移文件位于 `src/main/resources/db/migration/`，启动时由 Flyway 自动执行。
- Hibernate `ddl-auto=validate`：仅校验实体与 schema 一致，不自动建表。
- 表结构与原 Prisma 迁移对齐（驼峰列名加双引号），主要表：`users` / `roles` / `permissions`（自引用树）/ `user_roles` /
  `role_permissions` / `files`。
