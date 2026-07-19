# 快速开始

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 与 `build.gradle.kts` 的 `toolchain.languageVersion` 一致 |
| Docker | 任意稳定版 | 用于启动 PostgreSQL 与 Redis |
| Gradle | 9.x（项目自带 `gradlew`） | 无需本地安装，使用 `./gradlew` |

> 无需本地安装 PostgreSQL / Redis，通过 `docker compose` 启动即可。

## 步骤一：准备配置

```bash
# 克隆项目
git clone https://github.com/shawicx/scx-backend.git
cd scx-backend

# 复制环境变量模板
cp .env.example .env
```

按需修改 `.env`（数据库密码、JWT 密钥、邮件配置等），关键字段见 [配置说明](./configuration.md)。

开发环境最小配置（默认值已可运行）：

```bash
SPRING_PROFILES_ACTIVE=dev
DB_HOST=localhost
DB_PORT=5433
DB_USERNAME=scx
DB_PASSWORD=             # 本地 trust 连接可留空
DB_DATABASE=scx-backend
REDIS_HOST=localhost
REDIS_PORT=6388
JWT_SECRET=please_change_to_a_strong_random_secret
ADMIN_INITIAL_PASSWORD=changeme123
```

## 步骤二：启动依赖

通过 `docker compose` 启动 PostgreSQL（5433）与 Redis（6388）：

```bash
docker compose up -d
```

验证服务健康：

```bash
docker compose ps
# 两个容器均应处于 healthy 状态
```

> 端口说明：PG 使用 5433、Redis 使用 6388，与项目约定一致（容器内监听同端口，避免与本机其他实例冲突）。

## 步骤三：运行应用

### 方式 A：Gradle bootRun（推荐开发）

```bash
./gradlew bootRun
```

默认使用 `dev` profile，自动读取项目根目录的 `.env` 文件。

### 方式 B：IntelliJ IDEA

运行主类 `com.scx.backend.ScxBackendApplication`，IDEA 会自动读取 `.env`。

### 方式 C：java -jar

```bash
./gradlew bootJar
java -jar build/libs/scx-backend-0.0.1-SNAPSHOT.jar
```

### 方式 D：Docker 镜像

```bash
./gradlew bootBuildImage
# 镜像默认使用 prod profile，通过 -e 注入环境变量
```

### 四种启动方式对比

| 方式 | Profile | 配置来源 | 适用场景 |
| --- | --- | --- | --- |
| IDEA 运行 | dev | `.env` | 本地开发调试 |
| `./gradlew bootRun` | dev（可 `SPRING_PROFILES_ACTIVE` 覆盖） | `.env` | 命令行开发 |
| `java -jar` | 由 `SPRING_PROFILES_ACTIVE` 决定 | `.env` / OS 环境变量 | 部署 |
| `bootBuildImage` | prod（内置默认） | OS 环境变量 / Secret | 容器化生产 |

## 步骤四：验证

应用启动后，监听 `http://localhost:3000`，全局路由前缀 `/api`。

```bash
# 健康检查
curl http://localhost:3000/api/health

# Swagger UI（浏览器访问）
open http://localhost:3000/api/swagger-ui.html

# OpenAPI JSON
curl http://localhost:3000/api/v3/api-docs
```

启动时 `SeedService` 会幂等创建超级管理员账号：

- 邮箱：`scx-super-admin@system.internal`
- 密码：`.env` 中的 `ADMIN_INITIAL_PASSWORD`（默认 `changeme123`）

## 步骤五：运行测试

```bash
# 全部测试（需启动 PG / Redis）
./gradlew test

# 仅编译（不运行）
./gradlew compileKotlin
```

测试分单元测试与集成测试两类，详见 [开发规范 - 测试](./development-guide.md#测试规范)。

## 步骤六：构建

```bash
# 清理 + 构建（含测试）
./gradlew clean build

# 仅打包 jar
./gradlew bootJar

# 构建容器镜像
./gradlew bootBuildImage
```

构建产物：

- `build/libs/scx-backend-0.0.1-SNAPSHOT.jar` — 可执行 jar
- `build/libs/scx-backend-0.0.1-SNAPSHOT-plain.jar` — 普通 jar（无内嵌依赖）

## 常见问题

### Flyway checksum mismatch

```
Migration checksum mismatch for migration version 1
```

**原因**：已应用的 migration 文件被修改。**已应用的 migration 文件内容不可更改**（包括注释、空格）。

**解决**：回退 migration 文件到原始内容，或对开发库执行 `flyway repair`。详见 [数据库设计 - Flyway 规范](./database.md#flyway-迁移规范)。

### 端口被占用

```bash
# 查看 3000 / 5433 / 6388 占用
lsof -i :3000 -i :5433 -i :6388
```

### Swagger 显示旧文档

IDEA 启动的应用不会自动热重载代码改动。修改 Controller 注解后需**重启应用**才能在 Swagger UI 生效。

## 相关文档

- [配置说明](./configuration.md) — 完整环境变量与配置项
- [目录结构](./project-structure.md) — 源码组织方式
