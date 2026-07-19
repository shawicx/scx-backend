# 目录结构

项目遵循 Maven 标准目录结构，源码位于 `src/main/kotlin/com/scx/backend/`。

## 顶层结构

```
scx-backend/
├── .wiki/                    # 项目文档（本目录）
├── docs/                     # 其他文档资源
├── gradle/                   # Gradle Wrapper
├── logs/                     # 运行日志（gitignore）
├── src/
│   ├── main/
│   │   ├── kotlin/com/scx/backend/   # 主代码
│   │   └── resources/                 # 配置与资源
│   └── test/
│       ├── kotlin/com/scx/backend/   # 测试代码
│       └── resources/
├── .env                      # 环境变量（gitignore）
├── .env.example              # 环境变量模板
├── .gitignore
├── AGENTS.md                 # AI Agent 规则
├── README.md
├── build.gradle.kts          # Gradle 构建脚本
├── docker-compose.yml        # PG + Redis 编排
├── gradle.properties
├── gradlew / gradlew.bat
└── settings.gradle.kts
```

## 主代码结构

`src/main/kotlin/com/scx/backend/`：

```
├── ScxBackendApplication.kt     # 启动入口（@SpringBootApplication）
│
├── common/                      # 通用基础设施（与业务无关）
│   ├── constants/               # 常量定义
│   │   ├── CacheKeys.kt         #   Redis Key 命名
│   │   └── TtlConstants.kt      #   TTL 常量（毫秒）
│   ├── decorator/               # 自定义注解
│   │   └── Public.kt            #   @Public（免鉴权标记）
│   ├── exception/               # 异常体系
│   │   ├── SystemException.kt   #   业务异常基类
│   │   ├── SystemErrorCode.kt   #   错误码枚举
│   │   └── GlobalExceptionHandler.kt  # 全局异常处理
│   ├── response/                # 响应封装
│   │   ├── ApiResponse.kt       #   统一响应结构
│   │   └── GlobalResponseHandler.kt   # 响应包装器
│   ├── util/                    # 工具类
│   │   ├── IdGenerator.kt       #   ULID 生成器
│   │   ├── IpUtils.kt           #   客户端 IP 提取
│   │   └── CryptoUtil.kt        #   AES 加解密
│   └── web/                     # Web 相关
│       └── AccessLogInterceptor.kt    # 访问日志拦截器
│
├── config/                      # 配置类
│   ├── AppProperties.kt         # app.* 属性绑定
│   ├── SwaggerProperties.kt     # swagger.* 属性绑定
│   ├── OpenApiConfig.kt         # OpenAPI 文档配置
│   ├── WebConfig.kt             # CORS + 拦截器注册
│   └── JacksonConfig.kt         # Jackson 序列化配置
│
├── entity/                      # JPA 实体（6 张表）
│   ├── User.kt
│   ├── Role.kt
│   ├── Permission.kt            # 自引用树
│   ├── UserRole.kt              # 用户-角色关联
│   ├── RolePermission.kt        # 角色-权限关联
│   └── File.kt
│
├── repository/                  # Spring Data JPA Repository（6 个）
│   ├── UserRepository.kt
│   ├── RoleRepository.kt
│   ├── PermissionRepository.kt
│   ├── UserRoleRepository.kt
│   ├── RolePermissionRepository.kt
│   └── FileRepository.kt
│
├── security/                    # 安全层
│   ├── Public.kt                # @Public 注解（注：实际在 common/decorator）
│   ├── Admin.kt                 # @Admin 注解
│   ├── AuthPrincipal.kt         # 认证主体（内嵌于 TokenAuthenticationFilter）
│   ├── SecurityConfig.kt        # Spring Security 配置
│   ├── TokenAuthenticationFilter.kt  # 令牌解析过滤器
│   ├── AuthInterceptor.kt       # 鉴权拦截器
│   └── AdminInterceptor.kt      # 管理员拦截器
│
└── modules/                     # 业务模块（按领域划分）
    ├── auth/                    # 认证（令牌服务）
    ├── user/                    # 用户
    │   ├── UserController.kt
    │   ├── UserService.kt
    │   └── dto/
    ├── role/                    # 角色
    │   ├── RoleController.kt
    │   ├── RoleService.kt
    │   └── dto/
    ├── permission/              # 权限
    │   ├── PermissionController.kt
    │   ├── PermissionService.kt
    │   └── dto/
    ├── rolepermission/          # 角色-权限关联
    │   └── RolePermissionService.kt
    ├── userrole/                # 用户-角色关联
    │   └── UserRoleService.kt
    ├── mail/                    # 邮件
    │   ├── MailController.kt
    │   ├── MailService.kt       # 接口
    │   ├── SmtpMailService.kt   # SMTP 实现
    │   ├── StubMailService.kt   # Stub 实现
    │   └── dto/
    ├── file/                    # 文件（空壳）
    │   ├── FileController.kt
    │   ├── FileService.kt
    │   └── dto/
    ├── cache/                   # 缓存封装
    │   ├── CacheService.kt
    │   └── RedisConfig.kt
    ├── health/                  # 健康检查
    │   ├── HealthController.kt
    │   └── HealthService.kt
    └── seed/                    # 种子数据
        └── SeedService.kt
```

## 资源目录

`src/main/resources/`：

```
├── application.yml              # 主配置（占位符引用环境变量）
├── application-dev.yml          # dev profile 特化
├── application-prod.yml         # prod profile 特化
├── logback-spring.xml           # 日志配置
├── db/
│   └── migration/
│       └── V1__init_schema.sql  # Flyway 初始迁移
└── templates/
    └── mail/                    # Thymeleaf 邮件模板
        ├── verification-code.html
        ├── welcome.html
        └── password-reset.html
```

## 包职责约定

| 包 | 职责 | 依赖方向 |
| --- | --- | --- |
| `common` | 与业务无关的基础设施 | 被所有层依赖 |
| `config` | Spring 配置类 | 依赖 common / security |
| `entity` | JPA 实体，映射数据库表 | 被 repository / modules 依赖 |
| `repository` | 数据访问层 | 依赖 entity |
| `security` | 认证鉴权基础设施 | 依赖 common / modules（AdminInterceptor 需 UserService） |
| `modules` | 业务逻辑，按领域聚合 | 依赖上述所有包 |

## 测试结构

`src/test/kotlin/com/scx/backend/` 镜像 main 结构：

```
├── ScxBackendApplicationTests.kt       # 上下文加载测试
├── common/                             # common 层单元测试
│   ├── ExceptionHandlerTest.kt
│   ├── ResponseHandlerTest.kt
│   └── util/CryptoUtilTest.kt
├── modules/                            # 模块集成测试
│   ├── auth/AuthServiceTest.kt
│   ├── cache/CacheServiceTest.kt
│   ├── health/HealthModuleTest.kt
│   ├── mail/MailModuleTest.kt
│   ├── role/RolePermissionIntegrationTest.kt
│   └── user/UserFlowIntegrationTest.kt
├── repository/SchemaValidationTest.kt  # 实体-schema 一致性
└── security/SecurityIntegrationTest.kt # 鉴权集成测试
```

详见 [开发规范 - 测试](./development-guide.md#测试规范)。

## 相关文档

- [架构概览](./architecture.md) — 各层如何协作
- [开发规范](./development-guide.md) — 新增代码应放在哪里
