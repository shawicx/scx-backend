# 部署与运维

两套环境：**本地全栈**（docker-compose，自带基础设施）与**生产**（GitHub Actions → 阿里云 ACR → ECS，共享 scx-infra 基础设施）。

## 本地：docker-compose 全栈

`docker-compose.yml` 编排 8 个服务 + 3 个卷：

| 服务 | 镜像 | 端口 | 要点 |
| --- | --- | --- | --- |
| postgres | postgres:16-alpine | 5433（容器内同端口） | user/db = `scx` / `scx-backend` |
| redis | redis:7-alpine | 6388（容器内同端口） | — |
| minio | minio/minio:latest | 9000 API / 9001 控制台 | root 凭据取 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| identity-service | 本地构建 | 3001 | 依赖 postgres + redis healthy |
| rbac-service | 本地构建 | 3002 | 依赖 postgres |
| notification-service | 本地构建 | 3003 | `MAIL_ENABLED=false`（Stub） |
| file-service | 本地构建 | 3004 | `MINIO_ENDPOINT=http://minio:9000`，预签名用 `http://localhost:9000` |
| gateway | 本地构建 | 8080（唯一对外） | `*_BASE_URL` 指向容器服务名；依赖 redis + 各服务 started |

```bash
# 构建镜像（Boot 4 的 bootBuildImage 默认 tag 是 latest 而非 version，默认 prod profile）
./gradlew :gateway:bootBuildImage :identity-service:bootBuildImage \
  :rbac-service:bootBuildImage :notification-service:bootBuildImage \
  :file-service:bootBuildImage -x test

docker compose up -d        # 对外入口 http://localhost:8080/api
```

镜像默认名：`docker.io/library/<模块名>:0.0.1-SNAPSHOT`（compose 引用）。

## 生产：GitHub Actions → 阿里云 ECS

流水线：`.github/workflows/deploy.yml`，**push 到 main 自动触发**（或 workflow_dispatch 手动）。

```
Job 1  build-and-push（ubuntu-latest）
  JDK 21 → ./gradlew :<5 个服务>:bootBuildImage --no-daemon -x test
  → docker login 阿里云 ACR
  → 每个镜像打两个 tag 推送：latest 与 sha-<commit前7位>（可回滚）

Job 2  deploy（SSH 到 ECS）
  → 校验 ECS 本地 .env 存在（ECS_ENV_FILE 路径）
  → docker pull 5 个镜像
  → 校验共享基础设施容器 scx-postgres / scx-redis 运行中
  → 按下游先、网关后的顺序 docker run 5 个容器
  → 健康检查：轮询 http://localhost:8080/api/health（最多 180 秒）
```

### 依赖的 GitHub Secrets

`ACR_REGISTRY` / `ACR_NAMESPACE` / `ACR_USERNAME` / `ACR_PASSWORD`、`ECS_HOST` / `ECS_USER` / `ECS_SSH_KEY` / `ECS_ENV_FILE`（ECS 上 .env 文件路径）。工作流显式撤销 GITHUB_TOKEN 全部权限（least-privilege）。

## 生产环境拓扑（ECS，host 网络）

```
客户端 → gateway(8080) → identity(3001) / rbac(3002) / notification(3003) / file(3004)
                          各服务 localhost:<port> 互通（--network host）
scx-infra（独立部署于 /opt/scx-infra）：
  scx-postgres —— 单实例多库（scx-backend / scx-service / scx-stock），host 网络仅绑 127.0.0.1
  scx-redis    —— db 编号隔离（backend=0 / service=1 / stock=2）
```

要点：

- **仅网关对外暴露**；下游通过 localhost 直连（host 网络）
- PG/Redis 由 **scx-infra 统一提供**，本仓库部署脚本只校验其运行（`docker ps` 检查 `scx-postgres` / `scx-redis`），不自建；未运行时提示 `cd /opt/scx-infra && docker compose up -d`
- MinIO 与 SMTP 等其它依赖按 ECS `.env` 配置
- **安全模型**：生产秘钥（DB/JWT/MAIL 等）只存在于 ECS 本地 `.env`，通过 `docker run --env-file` 注入；GitHub 仅保存 ACR 与 SSH 凭证

### 容器资源与 JVM 参数（ECS 总内存约 4G）

| 容器 | 内存限制 | 说明 |
| --- | --- | --- |
| scx-identity | `-m 640m` | 最重（JPA + Security + Mail） |
| scx-rbac / scx-notification / scx-file / scx-gateway | `-m 512m` | 其余各 512m |

统一注入（绕开 Paketo BPE 默认值）：

- `BPL_JVM_THREAD_COUNT=50`（默认 250，线程栈挤占内存）
- `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=128M -XX:MaxRAMPercentage=50`（CodeCache 240M→128M + heap 限容器内存 50%）

> ⚠️ 部署脚本实现细节（曾踩坑）：JVM 参数必须**内联**在各条 `docker run` 命令中——收进 shell 变量再无引号展开时，`JAVA_TOOL_OPTIONS` 值内的空格会把 `-XX:*` 拆散泄漏成 docker flag（`unknown shorthand flag: 'X'`）导致部署失败。

### 回滚方式

重新部署历史镜像：手动 `docker run` 指定 `sha-<commit>` tag（镜像双 tag 均保留在 ACR），或 revert commit 后由流水线自动重发。

## 健康检查与排障

- 业务健康：`GET http://localhost:8080/api/health`（经网关到 identity，依赖 PG + Flyway + 网关路由就绪；网关 buildpack 初始化约 35s）
- 网关路由排查：Actuator gateway 端点（`/actuator/gateway`，网关容器内）
- 部署失败时流水线会输出：容器状态、ECS 内存（free）、OOM Killer 记录（dmesg）、gateway 与 identity 最近日志

## 相关文档

- [local-development](../02-getting-started/local-development.md) — 本地全栈启动
- [configuration](../06-configuration/configuration.md) — 生产 .env 键清单
- [gateway](../04-api/gateway.md) — 网关路由配置
