# 架构概览

## 分层架构

项目采用经典的四层架构，自上而下依赖单向流动：

```
┌─────────────────────────────────────────────────────┐
│  HTTP 请求（/api/**）                                │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│  Filter 层（Servlet 容器）                           │
│  TokenAuthenticationFilter —— 解析 Bearer 令牌       │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│  Interceptor 层（Spring MVC）                        │
│  AuthInterceptor → AdminInterceptor → AccessLog     │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│  Controller 层（modules/*/Controller）               │
│  参数校验、调用 Service、返回 DTO                    │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│  Service 层（modules/*/Service）                     │
│  业务逻辑、事务边界、调用 Repository / Cache         │
└──────────────────────┬──────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────┐
│  Repository 层（repository/）                        │
│  Spring Data JPA，数据库访问                         │
└──────────────────────┬──────────────────────────────┘
                       ▼
                PostgreSQL + Redis
```

**横向支撑层**：

- `common/` — 通用基础设施：响应封装、异常处理、工具类、常量、注解
- `config/` — 配置类：Jackson、OpenAPI、Web MVC、属性绑定
- `security/` — 安全：SecurityConfig、Filter、Interceptor、注解
- `entity/` — JPA 实体

## 请求生命周期

一次 HTTP 请求从进入到响应返回，完整经过以下环节：

```
1. Tomcat 接收请求
   ↓
2. Spring Security FilterChain
   ↓ TokenAuthenticationFilter：若携带 Bearer token，解析并写入 SecurityContext
   ↓ （此处仅解析，不强制鉴权）
3. DispatcherServlet 路由到 Controller
   ↓
4. HandlerInterceptor 链（按 order 执行）
   ├─ AuthInterceptor（HIGHEST_PRECEDENCE）：@Public 放行，否则校验已认证
   ├─ AdminInterceptor（HIGHEST_PRECEDENCE+1）：@Admin 校验管理员角色
   └─ AccessLogInterceptor：记录访问日志
   ↓
5. Controller 方法
   ↓ @Valid 参数校验（jakarta.validation）
   ↓ 调用 Service
   ↓
6. Service 业务逻辑（@Transactional 事务）
   ↓ 操作 Repository / CacheService
   ↓
7. Controller 返回 DTO
   ↓
8. GlobalResponseHandler（ResponseBodyAdvice）
   ↓ 将 DTO 包装为统一 ApiResponse 结构
   ↓
9. 返回 HTTP 响应
```

**特殊路径**（跳过鉴权与包装）：

- `/api/v3/api-docs*`、`/api/swagger*`、`/api/webjars*` — Swagger 文档，不包装响应
- `/api/actuator/**` — Actuator 端点，Spring Security 直接放行

## 模块拓扑

业务模块位于 `modules/` 下，按领域划分。模块间依赖关系：

```
                    ┌──────────┐
                    │   user   │ ◄── 用户登录、角色分配、权限查询
                    └────┬─────┘
            ┌───────────┼───────────┐
            ▼           ▼           ▼
       ┌────────┐ ┌─────────┐ ┌──────────┐
       │  auth  │ │ userrole│ │   role   │
       │ (令牌) │ │ (关联)  │ │          │
       └────────┘ └────┬────┘ └────┬─────┘
                         │           │
                    ┌────▼────┐ ┌────▼──────────┐
                    │  role   │ │ rolepermission│
                    │ (权限)  │ │   (关联)      │
                    └─────────┘ └───────────────┘
                                       │
                                  ┌────▼──────┐
                                  │permission │
                                  │ (树形)    │
                                  └───────────┘
```

**横向依赖**：

- `user` → `auth`（登录签发令牌）、`mail`（发送验证码/欢迎邮件）、`cache`（验证码存储）
- `role` → `rolepermission`（分配权限）
- `seed` → `user`、`role`、`userrole`（启动时初始化超管）
- `health` → `cache`（Redis 探针）、`EntityManager`（DB 探针）

**独立模块**：`file`（空壳实现）、`cache`（基础封装）、`mail`（独立服务）

## 技术选型说明

| 关注点 | 选型 | 理由 |
| --- | --- | --- |
| ORM | Spring Data JPA + Hibernate | 标准化、生态成熟，配合 `Specification` 实现动态查询 |
| Schema 迁移 | Flyway | 版本化迁移，`ddl-auto=validate` 仅校验不自动建表 |
| 缓存 | Spring Data Redis（StringRedisTemplate） | 字符串透传 + JSON 序列化，避免类型绑定 |
| 安全 | Spring Security + 自研令牌 | 无状态、单点令牌，适配自研协议 |
| 文档 | Springdoc OpenAPI 3 | 注解驱动（`@Tag` / `@Operation` / `@Schema`），自动生成 |
| 模板 | Thymeleaf | 邮件模板渲染 |
| 序列化 | Jackson + jackson-module-kotlin | Kotlin data class 友好 |

## 相关文档

- [认证与鉴权](./authentication.md) — Filter / Interceptor 链的详细机制
- [业务模块](./modules.md) — 各模块的职责与实现细节
- [统一响应与错误码](./api-response.md) — 响应包装规则
