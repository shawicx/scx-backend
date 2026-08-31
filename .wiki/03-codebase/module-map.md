# 模块地图

按 **服务 × 业务模块** 组织（微服务拆分后不再有统一的 `modules/` 目录）。每个模块给出路径、核心类、接口与关键业务规则。

## 总览

| 服务 | 模块 | 核心类 | 职责 |
| --- | --- | --- | --- |
| identity | auth | `AuthService` | 令牌签发、校验、刷新、登出、加密密钥 |
| identity | user | `UserController` / `UserService` | 用户全生命周期 + 登录 + 角色分配 |
| identity | health | `HealthController` / `HealthService` | 业务侧健康检查 |
| identity | seed | `SeedService` | 启动时幂等创建超管 |
| identity | cache | `CacheService` / `RedisConfig` | Redis 封装（详见 [caching](../05-data/caching.md)） |
| rbac | role | `RoleController` / `RoleService` | 角色 CRUD 与权限分配 |
| rbac | permission | `PermissionController` / `PermissionService` | 树形权限（菜单/按钮） |
| rbac | rolepermission | `RolePermissionService` | 角色-权限关联 |
| notification | mail | `MailController` / `MailService` 双实现 | 邮件发送（详见 [mail](../07-integrations/mail.md)） |
| file | file | `FileController` / `FileService` / `MinioStorageService` | MinIO 文件管理 |

---

## identity — auth 认证

路径：`identity-service/src/main/kotlin/com/scx/backend/identity/auth/AuthService.kt`

| 方法 | 说明 |
| --- | --- |
| `generateAccessToken(userId, email, isAdmin)` | 签发 access 令牌（2h），`isAdmin` 嵌入 payload，写入 Redis |
| `generateRefreshToken(userId, email, isAdmin)` | 签发 refresh 令牌（7d） |
| `validateAccessToken(token)` / `validateRefreshToken(token)` | 校验签名 + type + Redis 单点令牌比对，返回 `TokenPayload(userId, email, isAdmin)` |
| `refreshTokens(refreshToken, isAdminProvider)` | 换新令牌对；**通过回调重算 isAdmin**（角色变更后刷新令牌即生效） |
| `logout(userId)` | 删除 Redis 中的 access / refresh 令牌 |
| `generateEncryptionKey()` / `getEncryptionKey(keyId)` | 前端密码加密密钥（32 字节 hex + ULID keyId，5 分钟有效） |

令牌协议与单点令牌机制详见 [authentication](../04-api/authentication.md)。

## identity — user 用户

路径：`identity-service/src/main/kotlin/com/scx/backend/identity/user/`

### 接口分组（前缀 `/users`，经网关为 `/api/users`）

| 分组 | 鉴权 | 接口 |
| --- | --- | --- |
| 公开（`@Public`） | 免鉴权 | `POST /register`、`POST /login`、`POST /login-password`、`GET /encryption-key`、`POST /send-login-code`、`POST /send-email-code` |
| 认证 | 需令牌 | `POST /logout`、`POST /refresh-token`、`POST /assign-role`、`POST /assign-roles-batch`、`DELETE /remove-role`、`GET /roles`、`GET /permissions`、`GET /check-role`、`GET /check-permission` |
| 管理员（`@Admin`） | 需管理员 | `GET /list`、`POST /create`、`DELETE /batch-delete`、`PATCH /toggle-status` |

### 关键业务规则

- **注册**：邮箱唯一（重复 → `9004`）；校验 Redis 验证码；BCrypt 存储；成功后异步发欢迎邮件（失败仅记日志）
- **邮箱验证码登录**：验证码存 `login_verification:{email}`（10 分钟）；成功更新 IP / 时间 / 登录次数
- **密码登录**：前端先 `GET /encryption-key` 取密钥，AES-256-CTR 加密；密钥不存在 → `9010`，解密失败 → `9011`，密码不符 → `9006`
- **批量删除**：软删除（置 `deletedAt`）；不能删自己；仅超管可删管理员
- **切换状态**：不能禁用自己
- **管理员判定**（`UserService.isAdmin`）：`SUPER_ADMIN` 角色，或角色 code 以 `ADMIN` 开头
- **数据隔离**：列表用 `Specification` 动态拼接并过滤 `deletedAt IS NULL`；搜索 email/name 不区分大小写；排序白名单 `createdAt/updatedAt/name/email`
- **过渡期耦合**：`UserService` 直接注入 rbac 的 `RoleRepository` / `PermissionRepository` 与 notification 的 `MailService`（后续计划改 REST）

## identity — health 健康检查

路径：`identity-service/src/main/kotlin/com/scx/backend/identity/health/`

`GET /api/health`（`@Public`，网关路由到 identity），与 Actuator `/api/actuator/health` 互补：

| 组件 | 检查方式 |
| --- | --- |
| 数据库 | `SELECT 1`（EntityManager 原生查询） |
| Redis | set + get + del 探针 |
| 系统 | JVM 版本、运行时间、内存、处理器 |

`status` 取值：`ok` / `degraded`（部分异常）/ `error`（整体异常）。

## identity — seed 种子数据

路径：`identity-service/src/main/kotlin/com/scx/backend/identity/seed/SeedService.kt`（实现 `ApplicationRunner`）

启动时幂等创建：`SUPER_ADMIN` 角色（`isSystem=true`）→ 超管用户 `scx-super-admin@system.internal`（密码取 `admin.initial-password`）→ 用户-角色关联。异常仅记日志不阻断启动。⚠️ 生产必须用 `ADMIN_INITIAL_PASSWORD` 覆盖默认密码。

## rbac — role 角色

路径：`rbac-service/src/main/kotlin/com/scx/backend/rbac/role/`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/roles/create` | POST | 创建角色 |
| `/roles/list` | GET | 分页列表 |
| `/roles/detail` | GET | 按 ID 查询 |
| `/roles/by-code` | GET | 按编码查询 |
| `/roles/update` | PUT | 更新 |
| `/roles/delete` | DELETE | 删除（`isSystem=true` 不可删） |
| `/roles/assign-permissions` | POST | 批量分配权限 |
| `/roles/permissions` | GET | 查询角色权限 |
| `/roles/remove-permission` | DELETE | 移除单个权限 |

规则：`name`、`code` 唯一；权限分配委托 `RolePermissionService`（自动去重）。

## rbac — permission 权限

路径：`rbac-service/src/main/kotlin/com/scx/backend/rbac/permission/`

树形权限自引用（`parentId`），`level` 自动计算：

| 场景 | 规则 |
| --- | --- |
| 无父 + MENU | level = 1（一级菜单） |
| 无父 + BUTTON | 报错（按钮必须有父节点） |
| 一级菜单父 + MENU | level = 2 |
| 非一级菜单父 + MENU | 报错（二级菜单必须挂一级菜单下） |
| 一/二级菜单父 + BUTTON | level = parent.level + 1 |
| 其他父 + BUTTON | 报错 |

最大层级 3。接口：CRUD + 树形查询（`/permissions/tree`、`/menu-tree`、`/level-1`、`/by-level`、`/{menuId}/buttons`）+ 辅助查询（`/actions`、`/resources`、`/search`、`/by-action`、`/by-resource`）。删除靠外键 `ON DELETE CASCADE` 级联删子节点。

## rbac — rolepermission 角色权限关联

路径：`rbac-service/src/main/kotlin/com/scx/backend/rbac/rolepermission/RolePermissionService.kt`

- `assignPermissionsToRole(roleId, permissionIds)` — 批量分配（过滤已存在，防唯一约束冲突）
- `removePermissionFromRole(roleId, permissionId)` — 移除单个
- `getPermissionsByRole(roleId)` — 查询角色权限

> 旧单体中的 `userrole`（UserRoleService）模块已在微服务迁移中删除；identity 仅保留 `user_roles` 表的实体与仓储（`UserRoleRepository.existsByUserIdAndRoleCodePrefix` 供管理员判定），目录 `identity/userrole/` 为空目录遗留。

## notification — mail 邮件

路径：`notification-service/src/main/kotlin/com/scx/backend/notification/mail/`

4 个 `@Public` 接口：`POST /mail/send-verification-code`、`/send-welcome-email`、`/send-password-reset`、`/send-html-email`。`mail.enabled` 切换 SMTP / Stub 双实现。详见 [mail](../07-integrations/mail.md)。

## file — 文件（MinIO）

路径：`file-service/src/main/kotlin/com/scx/backend/file/`（配置 `file-service/src/main/kotlin/com/scx/backend/file/config/MinioConfig.kt`，存储 `file-service/src/main/kotlin/com/scx/backend/file/storage/MinioStorageService.kt`）

桶保持私有，对外一律走临时预签名 URL（默认 1 小时，`MINIO_PRESIGN_EXPIRY`）；删除为软删除（置 `deletedAt`），MinIO 对象保留。

| 接口 | 说明 |
| --- | --- |
| `POST /files/upload` | 单文件上传（multipart `file`），返回含预签名直链的文件信息 |
| `POST /files/batch-upload` | 批量上传（multipart `files`），全成功或全失败 |
| `GET /files/list` | 分页列表（搜索 / MIME 过滤 / 排序；用户隔离，管理员可跨用户） |
| `GET /files/info` | 详情（软删除视为不存在，非本人返回权限不足） |
| `DELETE /files/batch-delete` | 批量软删除（跳过非本人/已删除项，返回受影响行数） |

关键逻辑：

- 对象键：`uploads/{yyyy}/{MM}/{dd}/{ULID}.{清洗后扩展名}`（扩展名白名单 `[a-z0-9]{1,10}`，非法省略）
- 失败补偿：落库失败移除已上传对象；批量任一失败清理全部并回滚
- 桶懒初始化：首次上传时确认/创建，启动期不依赖 MinIO 可用
- 双客户端：`minioClient`（服务内部连接）与 `minioUrlClient`（用浏览器可达的 `MINIO_PUBLIC_ENDPOINT` 生成预签名 URL）
- 上传限制：单文件 50MB / 单请求 100MB（超限由全局异常处理器返回 400）

## 相关文档

- [authentication](../04-api/authentication.md) — auth 模块的鉴权机制
- [caching](../05-data/caching.md) — cache 模块 API
- [mail](../07-integrations/mail.md) — mail 模块配置
- [database](../05-data/database.md) — 各模块对应的表
