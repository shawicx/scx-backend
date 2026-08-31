# 开发规范

本文档约定代码组织、命名、测试与扩展规范，新增代码应遵循（并遵守 [AGENTS.md](../../AGENTS.md) 的整体规则）。

## 包结构约定（微服务版）

业务代码按「服务 × 模块」组织：

```
<service>/src/main/kotlin/com/scx/backend/<域>/<模块>/
├── {Module}Controller.kt     # Controller（内部模块可无）
├── {Module}Service.kt        # Service（核心业务逻辑）
└── dto/                      # 数据传输对象
```

示例：`identity-service/src/main/kotlin/com/scx/backend/identity/user/`（UserController / UserService / dto）。

### 何处放置代码

| 代码类型 | 位置 |
| --- | --- |
| REST 接口 / 业务逻辑 / DTO | 对应服务的 `<模块>/` |
| JPA 实体 | `<service>/.../entity/` |
| 数据访问 | `<service>/.../repository/` |
| 服务内配置（MinIO 等） | `<service>/.../config/` |
| 安全配置 | `<service>/.../security/` |
| **跨服务共用**的协议/常量/工具（无 Servlet） | `common/src/main/kotlin/com/scx/backend/common/` |
| Servlet 通用基础设施（异常/响应处理、身份解析） | `common-web/` |

> 判断标准：被两个及以上服务用到 → common / common-web；仅本服务 → 服务内。

## 分层约定

### Controller 层

- ✅ 参数校验（`@Valid`）、调用 Service 返回 DTO、Swagger 注解（`@Tag` / `@Operation` / `@Schema`）、鉴权注解（`@Public` / `@Admin`）
- ❌ 不写业务逻辑、不直接操作 Repository

```kotlin
@Tag(name = "用户管理", description = "...")
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {
    @Operation(summary = "用户注册")
    @Public
    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: RegisterUserDto): UserResponseDto =
        userService.register(dto)   // 直接委托
}
```

### Service 层

- ✅ 业务逻辑、事务边界（`@Transactional`）、调用 Repository / CacheService / 其他 Service、抛 `SystemException`、**构造器注入**（不在内部 `new`）

### Repository 层

- 继承 `JpaRepository`，按需 `@Query` 或派生查询；复杂查询用 `Specification` 动态拼接

## DTO 命名规范

| 类型 | 命名 | 用途 |
| --- | --- | --- |
| 创建请求 | `Create{Entity}Dto` | POST 创建 |
| 更新请求 | `Update{Entity}Dto` | PUT 更新（含 id） |
| 查询请求 | `Query{Entity}Dto` | GET 列表查询参数 |
| 单项响应 | `{Entity}ResponseDto` | 详情/创建后返回 |
| 列表项 / 列表响应 | `{Entity}ListItemDto` / `{Entity}ListResponseDto` | 分页列表 |
| 通用消息 / 批量结果 | `MessageDto` / `CountResultDto` | 位于 `common/src/main/kotlin/com/scx/backend/common/dto/CommonDtos.kt` |

字段注解：校验用 `@field:NotBlank` / `@field:Email` 等（注意 `@field:` 前缀），文档用 `@Schema(description = "...", example = "...")`。

## 异常处理规范

使用 `SystemException` 伴生对象工厂方法（不要裸抛 RuntimeException）：

```kotlin
throw SystemException.emailExists()                    // 预设消息
throw SystemException.dataNotFound("用户不存在")         // 自定义消息
```

- ❌ 返回 `null` 表示错误；❌ Controller 里 try-catch 包 Service（交给全局处理器）；❌ 抛 `RuntimeException`
- 新增错误码：`SystemErrorCode` 枚举 code 取 **9014+**，同步维护 `mapToHttpStatus` 映射

## ID 生成

所有实体主键通过 `IdGenerator.nextId()`（common）生成 ULID，应用层赋值。

## 测试规范

### 现状（微服务迁移后仅 4 个测试类）

| 测试 | 模块 | 类型 |
| --- | --- | --- |
| `gateway/src/test/kotlin/com/scx/backend/gateway/GatewayContextTest.kt` | gateway | 上下文装配（RouteLocator + AuthGlobalFilter） |
| `file-service/src/test/kotlin/com/scx/backend/file/FileContextTest.kt` | file | 上下文装配（H2 PostgreSQL 模式占位、关 Flyway） |
| `file-service/src/test/kotlin/com/scx/backend/file/FileServiceTest.kt` | file | 纯单元（Mockito）：对象键、上传校验、归属隔离、软删除 |
| `notification-service/src/test/kotlin/com/scx/backend/notification/mail/MailModuleTest.kt` | notification | 集成（RANDOM_PORT + JDK HttpClient） |

> 单体时代的大量测试（AuthServiceTest、UserFlowIntegrationTest、SchemaValidationTest 等）随 `app` 模块删除，尚未迁移到 identity-service（待确认：迁移计划）。

### 约定

- 单元测试：无 Spring 容器或轻量 Mock（Mockito 5.x，注意无 `whenever` 扩展时用 `Mockito.lenient()` / `doReturn` 风格）
- 集成测试：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` + JDK HttpClient 打真实 HTTP；测试配置用 `@TestPropertySource` 注入；`@BeforeEach` / `@AfterEach` 做数据清理

### 命令

```bash
./gradlew test                          # 全部
./gradlew :file-service:test            # 单模块
./gradlew test --tests "*FileServiceTest"   # 单类
```

## 扩展指南

### 新增业务模块（服务内）

1. 建 `<模块>/` 包：Controller + Service + dto
2. 需要表：实体放 `entity/`，迁移放该服务 `src/main/resources/db/migration/V{N}__xxx.sql`（注意双份迁移的归属，见 [database](../05-data/database.md)）
3. Repository 继承 `JpaRepository`
4. 补 Swagger 注解与鉴权注解；如对外暴露，确认网关路由与 `PublicPaths` 白名单是否需要更新

### 新增微服务模块（参考 file-service 的拆分路径）

1. `settings.gradle.kts` 加入模块；新建 `<service>/build.gradle.kts`（依赖 `common-web`，按需 JPA/Security/Flyway）
2. 主类 `@SpringBootApplication`（gateway 的 scanBasePackages 已覆盖 `com.scx.backend`）
3. `src/main/resources/application.yml`（端口、context-path=/api、Swagger）
4. 网关加路由（`gateway/src/main/resources/application.yml` routes）
5. `docker-compose.yml` 加服务；`deploy.yml` 的 SERVICES 列表与 docker run 段同步
6. 安全配置参照 rbac/file（`HeaderAuthFilter` + `@ConditionalOnMissingBean`）

### 新增数据库表

1. Flyway 迁移 `V{N}__xxx.sql`（⚠️ 不改已应用文件；驼峰列名加双引号、ULID 主键、CASCADE 外键）
2. 实体与迁移字段一致；启动时 `ddl-auto=validate` 校验

## 代码风格

- `kotlin.code.style=official`（`gradle.properties`）
- 编译参数（根 `build.gradle.kts` 统一）：`-Xjsr305=strict`、`-Xannotation-default-target=param-property`（Kotlin 2.2，`@Schema` 等注解同时作用于参数与属性）
- 注释规范（AGENTS.md）：每个文件 `@description` 中文头注释；函数带 `@description` / `@param` / `@returns`，核心函数带 `@example`
- 日志：SLF4J 占位符（不拼接字符串）；异常对象作为最后一个参数

## Git 提交规范

commitlint 中文风格（`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `chore:` + 中文描述）：

- 提交前确保 `./gradlew compileKotlin` 通过
- 不自动提交：`git commit` 由人工执行（见 [AGENTS.md](../../AGENTS.md)）

## 相关文档

- [repository-structure](../03-codebase/repository-structure.md) — 代码放哪里
- [conventions](../04-api/conventions.md) — 异常与响应规范
- [database](../05-data/database.md) — 迁移与实体规范
