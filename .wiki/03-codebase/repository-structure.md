# 仓库结构

单仓多模块（Gradle），模块清单见 `settings.gradle.kts`：`common`、`common-web`、`gateway`、`identity-service`、`rbac-service`、`notification-service`、`file-service`。

## 顶层目录

```
scx-backend/
├── common/                  # 跨服务协议库（无 Servlet）
├── common-web/              # Servlet 基础设施库（依赖 common）
├── gateway/                 # API 网关（8080，WebFlux）
├── identity-service/        # 身份认证服务（3001）
├── rbac-service/            # 角色权限服务（3002）
├── notification-service/    # 邮件通知服务（3003）
├── file-service/            # 文件服务（3004，MinIO）
├── gradle/wrapper/          # Gradle Wrapper（9.3.0）
├── .github/workflows/       # CI/CD（deploy.yml → 阿里云 ECS）
├── .wiki/                   # 本 Wiki
├── docs/superpowers/        # 历史设计文档（被 .gitignore 忽略，仅本地）
├── build.gradle.kts         # 根构建：插件版本、BOM、编译选项、测试统一配置
├── settings.gradle.kts      # 模块清单
├── gradle.properties        # kotlin.code.style、代理禁用
├── docker-compose.yml       # 本地全栈编排（8 服务 + 3 卷）
├── .env.example             # 环境变量模板
├── README.md                # 单页概览
└── AGENTS.md                # AI 代码修改规则
```

> 根目录没有 `src/`——单体代码已迁移至各服务模块（过渡单体 `app` 于 commit `271c543` 删除）。

## common — 协议层（无 Servlet）

```
common/src/main/kotlin/com/scx/backend/common/
├── constants/
│   ├── CacheKeys.kt           # Redis Key 工厂（access_token:{userId} 等 5 个）
│   └── TtlConstants.kt        # TTL 常量（毫秒）
├── decorator/
│   └── Public.kt              # @Public 注解
├── dto/
│   └── CommonDtos.kt          # MessageDto / CountResultDto 等通用 DTO
├── exception/
│   ├── SystemErrorCode.kt     # 错误码枚举（9000-9013）+ HTTP 映射
│   └── SystemException.kt     # 业务异常（伴生对象工厂方法）
├── response/
│   └── ApiResponse.kt         # 统一响应结构
├── security/
│   ├── Admin.kt               # @Admin 注解
│   ├── AuthPrincipal.kt       # 认证主体
│   ├── PublicPaths.kt         # 公开路径白名单（网关与下游共用）
│   ├── TokenCodec.kt          # 令牌编解码（网关验签 / identity 签发共用）
│   └── TokenPayload.kt        # 令牌解析结果（userId/email/isAdmin）
├── util/
│   ├── CryptoUtil.kt          # AES-256-CTR（密码登录前端加密互通）
│   └── IdGenerator.kt         # ULID 生成器
└── config/
    └── JacksonConfig.kt       # jackson-module-kotlin 等序列化配置
```

## common-web — Servlet 基础设施

```
common-web/src/main/kotlin/com/scx/backend/common/
├── exception/
│   └── GlobalExceptionHandler.kt   # @RestControllerAdvice 全局异常处理
├── response/
│   └── GlobalResponseHandler.kt    # ResponseBodyAdvice 统一包装
├── security/
│   └── AuthContextResolver.kt      # 从 X-User-* 头解析 AuthPrincipal
├── util/
│   └── IpUtils.kt                  # 客户端 IP 提取
└── web/
    └── AccessLogInterceptor.kt     # 访问日志拦截器（⚠️ 当前未注册，见 authentication）
```

## gateway — API 网关

```
gateway/src/main/kotlin/com/scx/backend/gateway/
├── GatewayApplication.kt     # 主类（scanBasePackages="com.scx.backend"）
├── GatewayConfig.kt          # 注册 TokenCodec Bean（jwt.secret 与 identity 共享）
└── AuthGlobalFilter.kt       # 集中鉴权全局过滤器
```

配置：`gateway/src/main/resources/application.yml`（路由、CORS、Redis）。

## identity-service — 身份认证（3001）

```
identity-service/src/main/kotlin/com/scx/backend/identity/
├── IdentityApplication.kt
├── auth/
│   └── AuthService.kt            # 令牌签发/校验/刷新/登出/加密密钥
├── cache/
│   ├── CacheService.kt           # Redis 封装
│   └── RedisConfig.kt            # StringRedisTemplate Bean
├── entity/                       # User / UserRole / UserPreferences
├── health/
│   ├── HealthController.kt       # GET /health（@Public）
│   └── HealthService.kt
├── repository/                   # UserRepository / UserRoleRepository
├── security/
│   ├── SecurityConfig.kt         # SecurityFilterChain + CORS
│   ├── TokenAuthenticationFilter.kt
│   ├── AuthInterceptor.kt        # ⚠️ 未注册（迁移遗留）
│   └── AdminInterceptor.kt       # ⚠️ 未注册（迁移遗留）
├── seed/
│   └── SeedService.kt            # 启动幂等创建超管
└── user/
    ├── UserController.kt
    ├── UserService.kt            # 过渡期直连 rbac 仓储与 MailService
    └── dto/
```

> `identity/userrole/` 是迁移遗留的空目录（无代码）。

## rbac-service — 角色权限（3002）

```
rbac-service/src/main/kotlin/com/scx/backend/rbac/
├── RbacApplication.kt
├── entity/                       # Role / Permission / RolePermission
├── permission/
│   ├── PermissionController.kt
│   ├── PermissionService.kt
│   └── dto/
├── repository/
├── role/
│   ├── RoleController.kt
│   ├── RoleService.kt
│   └── dto/
├── rolepermission/
│   └── RolePermissionService.kt
└── security/
    └── RbacSecurityConfig.kt     # @ConditionalOnMissingBean + HeaderAuthFilter
```

迁移脚本：`rbac-service/src/main/resources/db/migration/V1__init_schema.sql`。

## notification-service — 邮件（3003）

```
notification-service/src/main/kotlin/com/scx/backend/notification/
├── NotificationApplication.kt
└── mail/
    ├── MailController.kt
    ├── MailService.kt            # 接口 + SendResult
    ├── SmtpMailService.kt
    ├── StubMailService.kt
    └── dto/MailDtos.kt
```

模板：`notification-service/src/main/resources/templates/mail/{verification-code,welcome,password-reset}.html`。无数据库、无 Spring Security。

## file-service — 文件（3004）

```
file-service/src/main/kotlin/com/scx/backend/file/
├── FileApplication.kt
├── FileController.kt
├── FileService.kt
├── config/
│   └── MinioConfig.kt            # 双 MinioClient（连接 + 预签名）
├── dto/FileDtos.kt
├── entity/File.kt
├── repository/FileRepository.kt
├── security/
│   └── FileSecurityConfig.kt     # 与 rbac 同模型（HeaderAuthFilter）
└── storage/
    └── MinioStorageService.kt    # 对象上传/删除/预签名
```

迁移脚本：`file-service/src/main/resources/db/migration/V1__init_schema.sql`（与 rbac 的一份内容相同）。

## 包结构分层约定

各服务统一遵循（与 [AGENTS.md](../../AGENTS.md) 的 Java/Kotlin 规则一致）：

| 代码类型 | 位置 |
| --- | --- |
| REST 接口 | `<service>/.../<模块>/<Module>Controller.kt` |
| 业务逻辑 | `<service>/.../<模块>/<Module>Service.kt` |
| 请求/响应 DTO | `<service>/.../<模块>/dto/` |
| JPA 实体 | `<service>/.../entity/` |
| 数据访问 | `<service>/.../repository/` |
| 服务内配置 | `<service>/.../config/` |
| 安全配置 | `<service>/.../security/` |
| 跨服务协议/工具 | `common/src/main/kotlin/com/scx/backend/common/` |
| Servlet 通用基础设施 | `common-web/...` |

## 根构建统一内容（`build.gradle.kts`）

- 插件版本：Kotlin 2.2.20、Spring Boot 4.0.7（BOM 用 `platform()` 引入）
- JDK toolchain 21
- 编译参数：`-Xjsr305=strict`、`-Xannotation-default-target=param-property`（Kotlin 2.2，解决 `@Schema` 注解目标问题）
- 测试统一 `useJUnitPlatform()`
- `bootBuildImage` 统一注入 `SPRING_PROFILES_ACTIVE=prod`

## 相关文档

- [module-map](./module-map.md) — 各模块职责与接口
- [architecture](../01-overview/architecture.md) — 模块依赖方向
- [development-guide](../08-development/development-guide.md) — 新代码放哪里
