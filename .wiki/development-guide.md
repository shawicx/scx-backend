# 开发规范

本文档约定项目开发过程中的代码组织、命名、测试与扩展规范，新增代码应遵循。

## 包结构约定

### 模块组织

业务模块统一位于 `modules/{module}/`，每个模块包含：

```
modules/{module}/
├── {Module}Controller.kt     # Controller（可选，内部模块无）
├── {Module}Service.kt        # Service（核心业务逻辑）
└── dto/                      # 数据传输对象（可选）
    └── {Module}Dtos.kt
```

**示例**：

```
modules/user/
├── UserController.kt
├── UserService.kt
└── dto/
    ├── UserDtos.kt           # 请求 DTO
    └── UserResponseDtos.kt   # 响应 DTO
```

### 何处放置代码

| 代码类型 | 位置 |
| --- | --- |
| REST 接口 | `modules/{module}/{Module}Controller.kt` |
| 业务逻辑 | `modules/{module}/{Module}Service.kt` |
| 请求/响应 DTO | `modules/{module}/dto/` |
| 数据库实体 | `entity/` |
| 数据访问 | `repository/` |
| 通用工具（无业务） | `common/util/` |
| 通用常量 | `common/constants/` |
| 配置类 | `config/` |
| 安全相关 | `security/` |

## 分层约定

### Controller 层

- ✅ 参数校验（`@Valid`）
- ✅ 调用 Service，返回 DTO
- ✅ Swagger 注解（`@Tag`、`@Operation`、`@Parameter`）
- ✅ 鉴权注解（`@Public` / `@Admin`）
- ❌ **不写业务逻辑**（如数据库查询、复杂条件判断）
- ❌ **不直接操作 Repository**（通过 Service）

```kotlin
@Tag(name = "用户管理", description = "...")
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @Operation(summary = "用户注册")
    @Public
    @PostMapping("/register")
    fun register(@Valid @RequestBody dto: RegisterUserDto): UserResponseDto =
        userService.register(dto)   // 直接委托给 Service
}
```

### Service 层

- ✅ 业务逻辑、事务边界（`@Transactional`）
- ✅ 调用 Repository / CacheService / 其他 Service
- ✅ 抛出 `SystemException`（业务异常）
- ✅ 构造器注入依赖（**不在内部 `new`**）

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,    // 构造器注入
    private val roleRepository: RoleRepository,
    // ...
) {
    @Transactional
    fun register(dto: RegisterUserDto): UserResponseDto {
        if (userRepository.existsByEmail(dto.email)) {
            throw SystemException.emailExists()    // 业务异常
        }
        // 业务逻辑...
    }
}
```

### Repository 层

- 继承 `JpaRepository`，按需添加 `@Query` 或派生查询方法
- 复杂查询使用 `Specification`（动态条件）

## DTO 命名规范

| 类型 | 命名 | 用途 |
| --- | --- | --- |
| 创建请求 | `Create{Entity}Dto` | POST 创建 |
| 更新请求 | `Update{Entity}Dto` | PUT 更新（含 id） |
| 查询请求 | `Query{Entity}Dto` | GET 列表查询参数 |
| 单项响应 | `{Entity}ResponseDto` | 详情/创建后返回 |
| 列表项 | `{Entity}ListItemDto` | 列表中的单项（精简字段） |
| 列表响应 | `{Entity}ListResponseDto` | 分页列表（含 total/page/limit） |
| 通用消息 | `MessageDto` | 仅返回提示消息 |
| 批量结果 | `CountResultDto` | 返回受影响行数 |

### 字段注解

- 校验：`@field:NotBlank`、`@field:Size`、`@field:Pattern`、`@field:Email` 等（注意 `@field:` 前缀）
- 文档：`@Schema(description = "...", example = "...", required = true)`

```kotlin
@Schema(description = "用户注册请求")
data class RegisterUserDto(
    @Schema(description = "邮箱地址", required = true)
    @field:Email(message = "请输入有效的邮箱地址")
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,
    // ...
)
```

## 异常处理规范

### 抛出业务异常

使用 `SystemException` 伴生对象的工厂方法：

```kotlin
throw SystemException.emailExists()                          // 预设消息
throw SystemException.dataNotFound("用户不存在")               // 自定义消息
throw SystemException.invalidCredentials("邮箱或密码错误")
throw SystemException.operationFailed("不能删除自己")
```

详见 [统一响应与错误码](./api-response.md)。

### 不要做的事

- ❌ 返回 `null` 表示错误（用异常）
- ❌ 在 Controller 用 `try-catch` 包裹 Service 调用（交给全局处理器）
- ❌ 抛出 `RuntimeException` 或 `Exception`（用 `SystemException` 的具体子类）

## ID 生成

所有实体主键通过 `IdGenerator.nextId()` 生成 ULID：

```kotlin
val user = User(
    id = IdGenerator.nextId(),    // 应用层生成，不依赖数据库
    // ...
)
```

详见 [数据库设计 - ULID 主键策略](./database.md#ulid-主键策略)。

## 测试规范

### 测试分类

| 类型 | 特征 | 示例 |
| --- | --- | --- |
| **单元测试** | 无 Spring 容器或轻量 Mock | `CryptoUtilTest`、`TtlConstantsTest` |
| **集成测试** | `@SpringBootTest`，需 DB/Redis | `AuthServiceTest`、`UserFlowIntegrationTest` |

### 集成测试约定

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:postgresql://localhost:5433/scx-backend-test",
    "spring.data.redis.port=6388",
])
class UserFlowIntegrationTest {
    @LocalServerPort private lateinit var port: Integer

    @BeforeEach
    fun cleanup() {
        // 测试前清理数据
        jdbcTemplate.execute("DELETE FROM users WHERE email LIKE '%@test.example.com'")
    }

    @AfterEach
    fun teardown() {
        // 测试后清理
    }
}
```

**要点**：

- 使用 `RANDOM_PORT` 避免端口冲突
- 通过 `@LocalServerPort` + JDK HttpClient 打真实 HTTP 请求
- `@BeforeEach` / `@AfterEach` 做数据隔离
- 测试专用配置通过 `@TestPropertySource` 注入

### 测试命令

```bash
./gradlew test                  # 全部测试
./gradlew test --tests "*AuthServiceTest"   # 指定测试类
```

## 扩展指南

### 新增业务模块

1. **创建包**：`modules/{newmodule}/`
2. **实体**（如需）：`entity/{NewEntity}.kt` + 迁移文件 `db/migration/V{N}__add_{table}.sql`
3. **Repository**：`repository/{NewEntity}Repository.kt`
4. **Service**：`modules/{newmodule}/{NewModule}Service.kt`
5. **Controller**（如需对外）：`modules/{newmodule}/{NewModule}Controller.kt` + `dto/`
6. **加 Swagger 注解**：`@Tag`、`@Operation`、`@Schema`

### 新增接口

1. 在对应 Controller 添加方法，标注 `@Operation`、HTTP 动词注解
2. 如需鉴权控制，标注 `@Public`（放行）或 `@Admin`（管理员）
3. 请求体定义 DTO，字段加 `@Valid` 校验与 `@Schema` 描述
4. 业务逻辑放 Service，Controller 仅委托

### 新增数据库表

1. **创建 Flyway 迁移**：`src/main/resources/db/migration/V{N}__{description}.sql`
   - ⚠️ **不要修改已应用的迁移文件**
   - 遵循现有命名风格（驼峰列名加双引号、ULID 主键、CASCADE 外键）
2. **创建实体**：`entity/{NewEntity}.t`，字段与迁移一致
3. **创建 Repository**：继承 `JpaRepository`
4. **验证**：启动应用，Hibernate `ddl-auto=validate` 会校验一致性

### 新增错误码

1. 在 `SystemErrorCode` 枚举中添加，code 在 `9014+` 范围
2. 在 `mapToHttpStatus` 添加 HTTP 映射
3. 在 `SystemException` 添加工厂方法（可选）

## 代码风格

- **Kotlin 官方风格**：`kotlin.code.style=official`
- **Jsr305 严格模式**：编译参数 `-Xjsr305=strict`
- **注解默认目标**：`-Xannotation-default-target=param-property`（Kotlin 2.2）
- **JSR-305 可空性**：注意 `@NotNull` / `@Nullable` 注解

### 日志

- 使用 SLF4J（`LoggerFactory.getLogger`）
- 占位符：`logger.info("用户登录: {}", userId)`（不用字符串拼接）
- 异常：`logger.error("操作失败: {}", key, e)`（异常对象作为最后一个参数）
- 日志级别：`com.scx.backend=DEBUG`（开发）、`INFO`（生产）

## Git 提交规范

沿用项目现有的 commitlint 中文风格：

```
feat: 新功能描述
fix: 修复描述
refactor: 重构描述
docs: 文档变更
test: 测试相关
chore: 构建/工具变更
```

- 提交前确保 `./gradlew compileKotlin` 通过
- 不自动提交（`git commit` 由人工执行，见 [AGENTS.md](../AGENTS.md)）

## 相关文档

- [目录结构](./project-structure.md) — 代码应放在哪里
- [统一响应与错误码](./api-response.md) — 异常与响应规范
- [数据库设计](./database.md) — 迁移与实体规范
- [AGENTS.md](../AGENTS.md) — AI 代码修改规则
