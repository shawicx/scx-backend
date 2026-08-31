# 项目概览

## 一句话定位

SCX Backend 是基于 Kotlin + Spring Boot 4 的微服务后端，通过 API 网关统一入口，提供用户认证、RBAC 权限、邮件通知、文件存储四类核心能力。

## 技术栈

| 分类 | 选型 | 版本（见 `build.gradle.kts` / 各模块 `build.gradle.kts`） |
| --- | --- | --- |
| 语言 / 运行时 | Kotlin / JDK | 2.2.20 / 21（Gradle toolchain 统一） |
| 应用框架 | Spring Boot | 4.0.7（根构建用 `platform()` BOM 引入） |
| 微服务框架 | Spring Cloud | 2025.1.2（仅 gateway，SCG server-webflux） |
| 构建 | Gradle（Kotlin DSL） | Wrapper 9.3.0，仓库自带 `gradlew` |
| API 网关 | Spring Cloud Gateway | WebFlux 栈，命名空间 `spring.cloud.gateway.server.webflux.*` |
| 数据库 | PostgreSQL | 16（Flyway 管理 schema，Hibernate `ddl-auto=validate`） |
| 缓存 | Redis | 7（StringRedisTemplate / ReactiveStringRedisTemplate） |
| 对象存储 | MinIO（S3 兼容） | SDK `io.minio:minio:9.0.3`（file-service） |
| 安全 | Spring Security + 自研令牌 | HMAC-SHA256 令牌（非标准 JWT），BCrypt 密码 |
| 邮件 | JavaMail + Thymeleaf | SMTP 与 Stub 双实现 |
| 文档 | Springdoc OpenAPI 3 | 3.0.3，各服务独立 Swagger UI |
| 加密 | BouncyCastle | bcprov 1.80（AES/密码学工具） |

## 核心能力

- **用户体系**：邮箱验证码登录、密码登录（前端 AES 加密传输）、注册、令牌刷新；用户增删改查与启停
- **RBAC 权限**：用户 ↔ 角色 ↔ 权限三层模型，权限支持菜单树与按钮，最大三级
- **认证安全**：自研 HMAC 令牌协议（payload 含 `isAdmin`）、单点令牌校验（Redis 比对）、网关集中鉴权、`@Public` / `@Admin` 注解
- **邮件服务**：验证码、欢迎、密码重置、自定义 HTML；`mail.enabled` 切换 SMTP / Stub
- **文件存储**：MinIO 上传/批量上传/列表/详情/软删除，私有桶 + 预签名 URL 访问
- **基础设施**：统一响应封装、全局异常处理、Flyway 迁移、ULID 主键、Swagger 文档

## 服务清单

7 个 Gradle 模块（`settings.gradle.kts`），其中 5 个可运行服务、2 个公共库：

| 模块 | 端口 | 职责 | 主类 |
| --- | --- | --- | --- |
| `gateway` | 8080（唯一对外入口） | 路由转发、集中鉴权、CORS | `gateway/src/main/kotlin/com/scx/backend/gateway/GatewayApplication.kt` |
| `identity-service` | 3001 | 用户、登录、令牌、健康检查、种子数据 | `identity-service/src/main/kotlin/com/scx/backend/identity/IdentityApplication.kt` |
| `rbac-service` | 3002 | 角色、权限（树形）、角色-权限关联 | `rbac-service/src/main/kotlin/com/scx/backend/rbac/RbacApplication.kt` |
| `notification-service` | 3003 | 邮件发送（无数据库） | `notification-service/src/main/kotlin/com/scx/backend/notification/NotificationApplication.kt` |
| `file-service` | 3004 | 文件管理（MinIO） | `file-service/src/main/kotlin/com/scx/backend/file/FileApplication.kt` |
| `common` | —（纯库） | 跨服务协议层：令牌编解码、错误码、响应结构、常量、工具（无 Servlet 依赖） | — |
| `common-web` | —（纯库） | Servlet 侧基础设施：全局异常/响应处理、身份解析、访问日志（依赖 common） | — |

四个业务服务均配置 `server.servlet.context-path: /api`，网关按 `/api/**` 路径原样转发。

## 演进背景

- **来源**：schema 移植自 Node 项目 scx-service 的 Prisma 迁移（见 `file-service/src/main/resources/db/migration/V1__init_schema.sql` 文件头注释，排除了 AI 相关的 `ai_requests` 表），错误码 9500 段（AI）不在本项目范围。
- **单体 → 微服务**：2026-07-30 起分步拆分（抽取 common → 邮件服务 → rbac → identity → 网关 → file），2026-08-05 删除过渡单体 `app` 模块（commit `271c543`）。设计决策记录在 `docs/superpowers/specs/2026-07-30-microservices-migration.md`（本地文档，被 .gitignore 忽略）。
- **过渡期现状**：identity 进程内依赖 `rbac-service` 与 `notification-service` 两个模块（直连表 / 直调 MailService），尚未改为 REST 调用；Swagger 用 `springdoc.packages-to-scan` 限定只扫 identity 自身包。详见 [architecture](./architecture.md)。

## 相关文档

- [architecture](./architecture.md) — 服务拓扑与请求生命周期
- [repository-structure](../03-codebase/repository-structure.md) — 各模块目录树
- [deployment](../09-deployment/deployment.md) — 部署形态
