# SCX Backend Wiki

> 基于 **Kotlin + Spring Boot 4** 的后端服务，提供用户、RBAC 权限、邮件、文件等核心能力。

## 核心能力

- **用户体系**：邮箱验证码登录、密码登录、注册、令牌刷新；用户增删改查与启停
- **RBAC 权限**：用户 ↔ 角色 ↔ 权限三层模型，权限支持菜单树与按钮
- **认证安全**：自研 HMAC 令牌协议，单点令牌校验，`@Public` / `@Admin` 注解式鉴权
- **邮件服务**：验证码、欢迎、密码重置、自定义 HTML，SMTP 与 Stub 双实现切换
- **基础设施**：PostgreSQL + Flyway 迁移、Redis 缓存、统一响应封装、全局异常处理、Swagger 文档

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 运行时 | Kotlin 2.2 / JDK 21 |
| 框架 | Spring Boot 4.0.7 |
| 构建 | Gradle（Kotlin DSL） |
| 数据库 | PostgreSQL 16（Flyway 管理迁移） |
| 缓存 | Redis 7 |
| 安全 | Spring Security + 自研 HMAC-SHA256 令牌 |
| 邮件 | JavaMail + Thymeleaf 模板 |
| 文档 | Springdoc OpenAPI 3（Swagger UI） |

## 文档导航

### 🚀 入门

- [快速开始](./getting-started.md) — 环境准备、依赖启动、运行与测试
- [目录结构](./project-structure.md) — 源码目录树与包职责
- [配置说明](./configuration.md) — Profile、环境变量、完整配置项

### 📐 深入

- [架构概览](./architecture.md) — 分层架构、请求生命周期、模块拓扑
- [认证与鉴权](./authentication.md) — 令牌协议、拦截器链、登录流程
- [业务模块](./modules.md) — 11 个模块的职责与关键逻辑
- [数据库设计](./database.md) — 表结构、ER 关系、ULID 主键、Flyway 规范
- [统一响应与错误码](./api-response.md) — 响应封装、错误码表、HTTP 映射
- [缓存设计](./caching.md) — CacheService API、Key 规范、TTL 常量
- [邮件服务](./mail.md) — 双实现切换、模板、SMTP 配置

### 📋 规范

- [开发规范](./development-guide.md) — 包结构、分层、DTO 命名、测试、扩展指南

## 快速链接

- Swagger UI：`http://localhost:3000/api/swagger-ui.html`
- 健康检查：`http://localhost:3000/api/health`
- 超级管理员账号：`scx-super-admin@system.internal`（密码由 `ADMIN_INITIAL_PASSWORD` 配置）
