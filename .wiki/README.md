# SCX Backend Wiki

> 基于 **Kotlin + Spring Boot 4** 的微服务后端：网关集中鉴权 + 身份认证 / RBAC 权限 / 邮件通知 / 文件存储四个业务服务。

本 Wiki 面向 AI 与开发人员，全部内容基于仓库当前代码与配置整理，代码引用均为仓库相对路径。

## 快速问答

| 问题 | 答案 | 详见 |
| --- | --- | --- |
| 项目是什么 | Gradle 多模块微服务后端（单体已于 2026-08 迁移为微服务） | [project-overview](./01-overview/project-overview.md) |
| 技术栈 | Kotlin 2.2.20 / JDK 21 / Spring Boot 4.0.7 / Spring Cloud 2025.1.2 | [project-overview](./01-overview/project-overview.md) |
| 有哪些服务 | gateway(8080) + identity(3001) + rbac(3002) + notification(3003) + file(3004)，公共库 common / common-web | [architecture](./01-overview/architecture.md) |
| 如何启动 | `docker compose up -d` 全栈，或 IDEA 逐服务启动 | [local-development](./02-getting-started/local-development.md) |
| 数据存哪 | PostgreSQL 16（Flyway 管 schema，单库 6 表）+ Redis 7（令牌/验证码）+ MinIO（文件） | [database](./05-data/database.md)、[caching](./05-data/caching.md) |
| 鉴权怎么做 | 网关集中验签（自研 HMAC 令牌）+ 注入 `X-User-*` 头，下游信任网关 | [gateway](./04-api/gateway.md)、[authentication](./04-api/authentication.md) |
| 配置从哪来 | 根目录 `.env`（`spring.config.import` 加载）+ 各服务 `application.yml` | [configuration](./06-configuration/configuration.md) |
| 如何部署 | 推 main → GitHub Actions 构建 5 镜像 → 阿里云 ACR → ECS（scx-infra 共享 PG/Redis） | [deployment](./09-deployment/deployment.md) |

## 目录导航

### 01 总览

- [project-overview](./01-overview/project-overview.md) — 项目定位、技术栈、服务清单、演进背景
- [architecture](./01-overview/architecture.md) — 微服务拓扑、模块依赖、请求生命周期、技术选型

### 02 入门

- [local-development](./02-getting-started/local-development.md) — 环境准备、全栈启动、逐服务启动、验证、FAQ

### 03 代码库

- [repository-structure](./03-codebase/repository-structure.md) — 7 个 Gradle 模块的目录树与包职责
- [module-map](./03-codebase/module-map.md) — 各服务业务模块的职责、核心类、接口与业务规则

### 04 API 与安全

- [gateway](./04-api/gateway.md) — 路由表、网关集中鉴权流程、`X-User-*` 头协议、白名单、CORS
- [authentication](./04-api/authentication.md) — 令牌协议、单点令牌、登录流程、下游安全配置差异
- [conventions](./04-api/conventions.md) — 统一响应结构、错误码表、HTTP 映射

### 05 数据

- [database](./05-data/database.md) — 6 张表结构、ER 关系、ULID 主键、Flyway 规范
- [caching](./05-data/caching.md) — CacheService API、Redis Key/TTL、db 隔离

### 06 配置

- [configuration](./06-configuration/configuration.md) — 环境变量全表、各服务配置要点、敏感配置清单

### 07 外部集成

- [mail](./07-integrations/mail.md) — SMTP/Stub 双实现、模板、超时控制、常用邮箱配置

### 08 开发

- [development-guide](./08-development/development-guide.md) — 分层约定、DTO 命名、测试、扩展指南、提交规范

### 09 部署

- [deployment](./09-deployment/deployment.md) — docker-compose 全栈、CI/CD 流水线、生产环境（ECS + scx-infra）

## 快速链接

- 网关入口（本地）：`http://localhost:8080/api`
- 业务健康检查：`GET http://localhost:8080/api/health`（网关转发到 identity）
- 各服务 Swagger（直连）：`http://localhost:3001/api/swagger-ui.html`（rbac 3002 / notification 3003 / file 3004 同理）
- 超级管理员账号：`scx-super-admin@system.internal`（密码由 `ADMIN_INITIAL_PASSWORD` 配置）

## 相关文档

- [README.md](../README.md) — 仓库单页概览
- [AGENTS.md](../AGENTS.md) — AI 代码修改规则
- [`.env.example`](../.env.example) — 环境变量模板
