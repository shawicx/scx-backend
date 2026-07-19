# AI Code Agent

以下规则适用于本项目，AI 在修改代码前必须严格遵守。

---

## 1. 核心原则

0. **先了解项目再动手**
   - 执行任务或修改代码之前，如果需要了解项目，必须先阅读 `.wiki/` 中的项目概述和文档结构
   - 掌握项目的整体架构和设计原则后再开始工作，避免基于猜测的修改

1. **保持现有功能完整性**
   - 除非用户明确要求，不得修改现有功能行为、配置、接口、环境变量结构、目录结构、脚手架流程
   - 保持项目原有的构建流程和运行方式

2. **最小化修改**
   - 只做必要的修改，避免影响不相关的功能模块
   - 新增逻辑必须最小化修改范围，避免连锁兼容问题

3. **代码风格统一**
   - 遵循项目现有的代码风格和命名规范
   - 文件名统一使用 kebab-case 或遵循项目现有约定
   - 保持文件和目录结构的一致性

---

## 2. 依赖管理

1. **禁止降级依赖版本**，只有用户明确允许或必须降级以修复冲突时才可降版本且经过用户允许
2. **不得移除现有依赖**，除非用户明确要求或明确冗余且经过用户允许
3. **新增依赖必须遵循最新兼容版本策略（semver ^）**，并考虑兼容性
4. **修改依赖配置前必须先确认项目类型与构建体系**

---

## 3. 输出要求

1. 提供清晰的代码实现，优先输出代码与必要的命令步骤
2. 必要时说明修改原因，避免冗余解释性文本
3. 不得擅自生成总结文档、README 或说明文档（除非用户明确要求）

---

## 4. 禁止事项

以下行为全部禁止：

- 自动执行 `git commit`、`git push` 等提交操作（代码修改完成后由用户审查并手动提交）
- 擅自重构项目一级目录结构（如 src、public、dist、apps 等）
- 改变 lint / build 行为导致结果不同
- 将项目改为其他框架或运行环境
- 删除现有功能
- 擅自改变基础配置文件（如 tsconfig / vite / webpack / Cargo.toml / pom.xml）
- 插入当前环境无法使用的 API（例如在浏览器项目引入 fs/path 等 node-only API）
- **Superpowers spec/plan 文件错放**：spec 与 plan 文件只能存放在 `docs/superpowers/specs/` 和 `docs/superpowers/plans/` 下；禁止从 `.gitignore` 中删除 `docs/superpowers` 条目；禁止将 spec/plan 文件放到任何其他目录

---

## 5. 注释规范

**示例（TypeScript）：**

```typescript
/**
 * @description 从 Apifox 平台获取 OpenAPI 数据
 * @param config API 配置对象
 * @returns Promise<ApiData> API 数据
 *
 * @example const data = await fetchApifoxData({ source: '...', token: '...' });
 *
 */
```

**强制要求：**

1. 每个文件必须有 `@description` 文件头注释（中文）
2. 每个函数必须有 `@description` 注释
3. 有参数的函数必须有 `@param` 注释
4. 有返回值的函数必须有 `@returns` 注释
5. 核心函数（命名策略、类型清理、生成器、解析器、转换器等）需要 `@example` 标签

**语言差异说明：**

- TypeScript / JavaScript：使用 JSDoc 风格（如上示例）
- Java：使用 Javadoc 风格（`/** ... */`），遵循 Java 既有规范
- Rust：使用 `///` 文档注释，遵循 rustdoc 规范（`# Arguments` / `# Returns` / `# Examples` 段）
- 各语言按各自规范实现上述 5 条要求的内容，不强制使用完全相同的标签名

---


---

## Java 项目规则

### 构建工具（二选一，遵循项目现状）

项目使用 Maven 或 Gradle，**不得混用**：

- Maven：`pom.xml` + `mvn` 命令
- Gradle：`build.gradle` 或 `build.gradle.kts` + `gradle` / `gradlew` 命令

### 运行时

1. **JDK 版本必须与 `pom.xml` 的 `<maven.compiler.release>` 或 `build.gradle` 的 `sourceCompatibility` 一致**
2. 不得擅自升级或降级 JDK target
3. Spring Boot 项目必须保留主版本一致性（如 Spring Boot 3.x 要求 JDK 17+）

### 项目结构约定（Maven Standard Layout）

遵循 Maven 标准目录结构，不得擅自调整一级目录：

- `src/main/java/` — 主代码（按包名分层）
  - `com/<org>/<project>/`
    - `controller/` — REST 控制器（Spring）
    - `service/` — 业务逻辑
    - `repository/` 或 `dao/` — 数据访问层
    - `model/` 或 `entity/` — 实体类
    - `config/` — 配置类
    - `dto/` — 数据传输对象
- `src/main/resources/` — 配置文件（application.yml / application.properties）
- `src/test/java/` — 单元测试（镜像 main 结构）
- `src/test/resources/` — 测试资源

### 构建与测试命令

Maven：
- `mvn clean install` — 清理并安装
- `mvn compile` — 编译
- `mvn test` — 运行测试
- `mvn package` — 打包
- `mvn spring-boot:run` — 运行 Spring Boot（如适用）

Gradle：
- `./gradlew build` — 构建
- `./gradlew test` — 运行测试
- `./gradlew bootRun` — 运行 Spring Boot（如适用）

### 依赖与配置规则

1. `pom.xml` / `build.gradle` 扩展依赖，不得重写整个文件
2. 依赖版本管理：Maven 通过 `<dependencyManagement>`，Gradle 通过版本目录或 ext 变量，遵循项目现状
3. Spring Boot 项目使用 `@Autowired` / 构造器注入，不得在内部 `new` Service
4. 配置文件修改采取合并策略，不破坏现有键值
5. JPA / MyBatis / Hibernate 等 ORM 遵循项目现状，不得擅自替换

### 禁止事项

- 不得在 Maven 项目中创建 `build.gradle`（或反之）
- 不得擅自升级或降级 JDK / Spring Boot 主版本
- 不得在 Controller 中编写业务逻辑（应放在 Service）
- 不得破坏包结构分层（如把 Service 放到 controller 包）
- 不得擅自切换 ORM 框架（JPA ↔ MyBatis）
- 不得引入与项目 Lombok 配置冲突的依赖
