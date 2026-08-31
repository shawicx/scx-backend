# 本地开发

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 与根 `build.gradle.kts` 的 toolchain 一致 |
| Docker | 任意稳定版 | 启动 PostgreSQL / Redis / MinIO 及全栈联调 |
| Gradle | 无需安装 | 仓库自带 `gradlew`（Wrapper 9.3.0） |

## 步骤一：准备配置

```bash
git clone https://github.com/shawicx/scx-backend.git
cd scx-backend
cp .env.example .env   # 按需修改数据库密码、JWT 密钥、邮件配置等
```

开发环境默认值即可运行：PG `localhost:5433`、Redis `localhost:6388`、MinIO `localhost:9000`。完整键说明见 [configuration](../06-configuration/configuration.md)。

## 步骤二：启动方式（二选一）

### 方式 A：docker compose 全栈（推荐，一键 8 个容器）

```bash
# 先构建 5 个服务镜像（Boot 4 的 bootBuildImage 默认 tag 是 latest，且默认 prod profile）
./gradlew :gateway:bootBuildImage :identity-service:bootBuildImage \
  :rbac-service:bootBuildImage :notification-service:bootBuildImage \
  :file-service:bootBuildImage -x test

docker compose up -d
```

compose 内容（`docker-compose.yml`）：

- 基础设施：`postgres`（16-alpine，宿主与容器均 5433）、`redis`（7-alpine，6388）、`minio`（9000 API / 9001 控制台）
- 应用：identity(3001) / rbac(3002) / notification(3003，`MAIL_ENABLED=false`) / file(3004) / gateway(8080)
- gateway 的 `*_BASE_URL` 指向容器服务名；file 的 `MINIO_ENDPOINT=http://minio:9000`、`MINIO_PUBLIC_ENDPOINT=http://localhost:9000`

验证：`docker compose ps`，基础设施三个容器应为 healthy。

### 方式 B：基础设施容器 + 本地进程（日常开发推荐）

只起基础设施，服务用 IDEA / Gradle 跑，便于断点调试：

```bash
docker compose up -d postgres redis minio
```

**IDEA 运行（推荐）**：直接运行各服务主类（`IdentityApplication` / `RbacApplication` / `NotificationApplication` / `FileApplication` / `GatewayApplication`），工作目录为仓库根时自动读取根目录 `.env`。

**gradlew bootRun**：`./gradlew :identity-service:bootRun` 等。注意 Gradle `bootRun` 的工作目录是**模块子目录**，`spring.config.import: "optional:file:.env"` 解析不到仓库根的 `.env`（实践中已踩坑）；如配置未生效，改用 IDEA 运行或将 `.env` 复制到模块目录。

启动顺序建议：基础设施 → identity（含 Flyway 迁移与种子数据）→ rbac / notification / file → gateway。

## 步骤三：验证

```bash
# 业务健康检查（经网关转发到 identity，检查 PG + Redis）
curl http://localhost:8080/api/health

# 各服务 Swagger（直连，不经网关）
open http://localhost:3001/api/swagger-ui.html   # identity
open http://localhost:3002/api/swagger-ui.html   # rbac
open http://localhost:3003/api/swagger-ui.html   # notification
open http://localhost:3004/api/swagger-ui.html   # file
```

首次启动 identity 时 `SeedService` 幂等创建超级管理员：

- 邮箱：`scx-super-admin@system.internal`
- 密码：`.env` 中的 `ADMIN_INITIAL_PASSWORD`（默认 `changeme123`，生产必须覆盖）

## 步骤四：测试与构建

```bash
./gradlew test                        # 全部测试（仅 4 个测试类，见开发规范）
./gradlew :file-service:test          # 单模块测试
./gradlew compileKotlin               # 仅编译（提交前自检）
./gradlew :<module>:bootBuildImage    # 构建容器镜像（默认 prod profile）
```

构建产物：`<module>/build/libs/<module>-0.0.1-SNAPSHOT.jar`（可执行）与 `-plain.jar`。

## 直连下游服务调试（绕过网关）

下游服务信任 `X-User-*` 头（本地直连时网关不在场，头不会被清理）。调试需要身份的接口时手动携带真实存在的用户 ULID：

```bash
curl http://localhost:3001/api/users/list \
  -H "X-User-Id: 01JXXXXXXXXXXXXXXXXXXXXXX" \
  -H "X-User-Email: scx-super-admin@system.internal" \
  -H "X-User-Admin: true"
```

> 注意：identity 端点级 `@Admin` 拦截器当前未挂载（迁移遗留，见 [authentication](../04-api/authentication.md)），`X-User-Admin: true` 主要影响 file 服务的跨用户查询等 `principal.isAdmin` 逻辑。

## 常见问题

### Flyway checksum mismatch

```
Migration checksum mismatch for migration version 1
```

已应用的迁移文件被修改导致。**已应用的迁移内容不可更改**（包括注释、空格）。解决：回退文件，或对开发库执行 `flyway repair`。详见 [database - Flyway 规范](../05-data/database.md#flyway-迁移规范)。

### 端口被占用

```bash
lsof -i :8080 -i :3001 -i :3002 -i :3003 -i :3004 -i :5433 -i :6388 -i :9000
```

### 预签名 URL 浏览器打不开

预签名 URL 用 `MINIO_PUBLIC_ENDPOINT` 生成（默认 `http://localhost:9000`）。服务跑在容器内时若该地址不可达，检查 compose 中 `MINIO_PUBLIC_ENDPOINT` 是否为宿主机可达地址。

### 修改 Controller 后 Swagger 未更新

IDEA 启动的应用不会热重载，改注解后需重启服务。

## 相关文档

- [configuration](../06-configuration/configuration.md) — 环境变量与各服务配置
- [deployment](../09-deployment/deployment.md) — 生产部署流程
- [repository-structure](../03-codebase/repository-structure.md) — 源码组织
