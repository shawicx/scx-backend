# 业务模块

业务模块位于 `src/main/kotlin/com/scx/backend/modules/`，按领域划分。本文档逐一说明各模块的职责、核心类与关键业务规则。

## 模块总览

| 模块 | 核心类 | 职责 |
| --- | --- | --- |
| [auth](#auth-认证) | `AuthService` | 令牌签发、校验、单点令牌管理 |
| [user](#user-用户) | `UserController` / `UserService` | 用户全生命周期 + 登录 |
| [role](#role-角色) | `RoleController` / `RoleService` | 角色 CRUD 与权限分配 |
| [permission](#permission-权限) | `PermissionController` / `PermissionService` | 树形权限（菜单/按钮） |
| [rolepermission](#rolepermission-角色权限关联) | `RolePermissionService` | 角色权限关联表 |
| [userrole](#userrole-用户角色关联) | `UserRoleService` | 用户角色关联表 |
| [mail](#mail-邮件) | `MailService` / `SmtpMailService` / `StubMailService` | 邮件发送 |
| [file](#file-文件) | `FileController` / `FileService` | 文件管理（空壳） |
| [cache](#cache-缓存) | `CacheService` / `RedisConfig` | Redis 封装 |
| [health](#health-健康检查) | `HealthController` / `HealthService` | 业务侧健康检查 |
| [seed](#seed-种子数据) | `SeedService` | 启动时初始化超管 |

---

## auth — 认证

**核心类**：`AuthService.kt`

**职责**：自研令牌协议的实现，提供令牌签发、校验、刷新、登出能力。

**主要方法**：

| 方法 | 说明 |
| --- | --- |
| `generateAccessToken(userId, email)` | 签发 access 令牌（2h），写入 Redis |
| `generateRefreshToken(userId, email)` | 签发 refresh 令牌（7d），写入 Redis |
| `validateAccessToken(token)` | 校验 access 令牌（签名 + Redis 比对） |
| `validateRefreshToken(token)` | 校验 refresh 令牌 |
| `refreshTokens(refreshToken)` | 用 refresh 换取新令牌对 |
| `logout(userId)` | 删除 Redis 中的令牌 |
| `generateEncryptionKey()` | 生成密码加密密钥（5 分钟有效） |

**关键规则**：

- 单点令牌：Redis 中缓存的令牌必须与请求令牌完全相等
- JSON 字段顺序固定：`userId, email, type, timestamp`
- 详见 [认证与鉴权](./authentication.md)

---

## user — 用户

**核心类**：`UserController.kt` / `UserService.kt` / `dto/`

**职责**：用户注册、登录、查询、角色分配、启停管理。

### 接口分组

| 分组 | 鉴权 | 接口 |
| --- | --- | --- |
| 公开 | `@Public` | 注册、邮箱验证码登录、密码登录、获取加密密钥、发送验证码 |
| 认证 | 默认 | 登出、刷新令牌、分配角色、查询角色/权限 |
| 管理员 | `@Admin` | 用户列表、创建用户、批量删除、切换状态 |

### 关键业务规则

**注册**：

- 邮箱唯一（重复 → `9004 EMAIL_EXISTS`）
- 校验邮箱验证码（Redis 比对，成功后删除）
- 密码 BCrypt 加密存储
- 注册成功后异步发送欢迎邮件（失败仅记日志，不阻断）

**邮箱验证码登录**：

- 用户必须存在且启用
- 验证码存于 `login_verification:{email}`（10 分钟有效）
- 登录成功更新 IP、时间、登录次数

**密码登录**：

- 必须先获取加密密钥（`keyId`），前端 AES 加密密码
- 解密失败 → `9010 KEY_EXPIRED` 或 `9011 DECRYPTION_FAILED`
- 密码错误 → `9006 INVALID_CREDENTIALS`（不区分"用户不存在"与"密码错误"）

**批量删除用户**：

- 软删除（设置 `deletedAt`），不物理删除
- 不能删除自己
- 仅超级管理员可删除其他管理员用户
- 删除时级联清理用户-角色关联

**切换状态**：

- 不能禁用自己

**管理员判定**（`isAdmin`）：

- `SUPER_ADMIN` 角色 → 管理员
- code 以 `ADMIN` 开头的角色 → 管理员

### 数据隔离

- 列表查询通过 `Specification` 动态拼接条件，自动过滤 `deletedAt IS NULL`
- 搜索字段：email、name（不区分大小写）
- 排序字段白名单：`createdAt`、`updatedAt`、`name`、`email`

---

## role — 角色

**核心类**：`RoleController.kt` / `RoleService.kt` / `dto/`

**职责**：角色 CRUD，以及为角色批量分配权限。

### 接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/roles/create` | POST | 创建角色 |
| `/roles/list` | GET | 分页列表 |
| `/roles/detail` | GET | 按 ID 查询 |
| `/roles/by-code` | GET | 按编码查询 |
| `/roles/update` | PUT | 更新角色 |
| `/roles/delete` | DELETE | 删除角色 |
| `/roles/assign-permissions` | POST | 批量分配权限 |
| `/roles/permissions` | GET | 查询角色权限 |
| `/roles/remove-permission` | DELETE | 移除单个权限 |

### 关键业务规则

- **唯一约束**：`name` 唯一、`code` 唯一
- **系统角色保护**：`isSystem=true` 的角色不可删除
- **权限分配**：委托给 `RolePermissionService`，自动去重、防重复

---

## permission — 权限

**核心类**：`PermissionController.kt` / `PermissionService.kt` / `dto/`

**职责**：树形权限管理，支持菜单（MENU）与按钮（BUTTON）两种类型。

### 树形结构规则

权限表自引用（`parentId`），`level` 字段自动计算：

| 场景 | 规则 |
| --- | --- |
| 无父 + MENU | level = 1（一级菜单） |
| 无父 + BUTTON | **报错**（按钮必须有父节点） |
| 一级菜单父 + MENU | level = 2（二级菜单） |
| 非一级菜单父 + MENU | **报错**（二级菜单必须挂在一级菜单下） |
| 一级或二级菜单父 + BUTTON | level = parent.level + 1（按钮挂在菜单下） |
| 其他父 + BUTTON | **报错**（按钮必须挂在一级或二级菜单下） |

最大层级：3（一级菜单 → 二级菜单 → 按钮）

### 接口

**CRUD**：创建、列表（分页+多条件过滤）、详情、更新、删除（级联删除子节点）

**树形查询**：

| 接口 | 说明 |
| --- | --- |
| `/permissions/tree` | 完整权限树（含 children） |
| `/permissions/menu-tree` | 精简菜单树（仅菜单字段，供前端导航） |
| `/permissions/level-1` | 所有一级菜单 |
| `/permissions/by-level` | 按层级查询 |
| `/{menuId}/buttons` | 指定菜单下的按钮 |

**辅助查询**：

- `/permissions/actions` — 所有去重的动作
- `/permissions/resources` — 所有去重的资源
- `/permissions/search` — 关键字搜索
- `/permissions/by-action`、`/permissions/by-resource` — 按动作/资源查询

### 级联删除

删除权限节点时，数据库外键 `ON DELETE CASCADE` 会自动删除所有子节点。

---

## rolepermission — 角色权限关联

**核心类**：`RolePermissionService.kt`

**职责**：维护角色与权限的多对多关联。

**关键方法**：

- `assignPermissionsToRole(roleId, permissionIds)` — 批量分配（自动去重、校验权限存在）
- `removePermissionFromRole(roleId, permissionId)` — 移除单个
- `getPermissionsByRole(roleId)` — 查询角色的权限列表

**防重复**：分配前过滤掉已存在的关联，避免唯一约束冲突。

---

## userrole — 用户角色关联

**核心类**：`UserRoleService.kt`

**职责**：维护用户与角色的多对多关联。

**关键方法**：

- 分配角色（单个/批量）、移除角色、查询用户角色
- `existsByUserIdAndRoleCode(userId, code)` — 用于 `hasRole` 判定
- `existsByUserIdAndRoleCodePrefix(userId, "ADMIN")` — 用于管理员判定

---

## mail — 邮件

**核心类**：`MailService.kt`（接口） / `SmtpMailService.kt` / `StubMailService.kt` / `MailController.kt`

**职责**：发送验证码、欢迎、密码重置、自定义 HTML 邮件。

**双实现切换**：

- `mail.enabled=true`（默认）→ `SmtpMailService`（真实发信，Thymeleaf 模板）
- `mail.enabled=false` → `StubMailService`（生成验证码但不发信，用于测试/无 SMTP 环境）

通过 `@ConditionalOnProperty` + `@Primary` 实现，详见 [邮件服务](./mail.md)。

**接口**（全部 `@Public`）：

- `/mail/send-verification-code` — 发送验证码
- `/mail/send-welcome-email` — 发送欢迎邮件
- `/mail/send-password-reset` — 发送密码重置邮件
- `/mail/send-html-email` — 发送自定义 HTML 邮件

---

## file — 文件

**核心类**：`FileController.kt` / `FileService.kt`

**状态**：⚠️ **空壳实现**。所有方法抛 `NotImplementedError`，待后续接入对象存储（OSS/S3/本地）时填充。

**接口**：

| 接口 | 状态 | 说明 |
| --- | --- | --- |
| `/files/upload` | ❌ 未实现 | 上传文件 |
| `/files/list` | ⚠️ 占位 | 列表查询（基于当前用户隔离） |
| `/files/info` | ⚠️ 占位 | 文件详情 |
| `/files/batch-delete` | ⚠️ 占位 | 批量删除 |

> 实体与数据库表已就绪，业务逻辑待实现。

---

## cache — 缓存

**核心类**：`CacheService.kt` / `RedisConfig.kt`

**职责**：封装 Redis 常用操作，提供统一的序列化与异常处理。

**核心特性**：

- `set` / `setWithMilliseconds` — String 原样存储，其它类型 JSON 序列化
- `get` — 自动尝试 JSON.parse，失败返回原始字符串
- 所有方法捕获异常并转换为 `RuntimeException`，记录日志

详见 [缓存设计](./caching.md)。

---

## health — 健康检查

**核心类**：`HealthController.kt` / `HealthService.kt`

**职责**：业务侧健康检查，返回结构化详情（区别于 Actuator）。

**接口**：`GET /api/health`（`@Public`）

**检查内容**：

| 组件 | 检查方式 |
| --- | --- |
| 数据库 | `SELECT 1`（EntityManager 原生查询） |
| Redis | set + get + del 探针 |
| 系统 | JVM 版本、运行时间、内存、处理器 |

**响应示例**：

```json
{
  "service": "scx-backend",
  "status": "ok",
  "timestamp": "2026-07-19T...",
  "database": { "status": "ok" },
  "redis": { "status": "ok" },
  "system": {
    "javaVersion": "21",
    "platform": "Mac OS X aarch64",
    "uptime": 12345,
    "availableProcessors": 8,
    "maxMemory": 4294967296,
    "usedMemory": 123456789
  },
  "responseTime": "5ms"
}
```

`status` 取值：`ok`（全部正常）、`degraded`（部分异常）、`error`（整体异常）。

---

## seed — 种子数据

**核心类**：`SeedService.kt`（实现 `ApplicationRunner`）

**职责**：应用启动时幂等创建超级管理员账号。

**初始化内容**：

1. `SUPER_ADMIN` 角色（`isSystem=true`，描述"系统内置超级管理员角色，拥有所有权限"）
2. 超级管理员用户（邮箱 `scx-super-admin@system.internal`，密码取 `admin.initial-password`）
3. 用户-角色关联

**幂等性**：每一步都先检查是否存在，已存在则跳过。

**失败处理**：异常仅记录日志，不阻断应用启动。

> ⚠️ 生产环境**必须**通过 `ADMIN_INITIAL_PASSWORD` 环境变量覆盖默认密码 `changeme123`。

## 相关文档

- [认证与鉴权](./authentication.md) — auth 模块的鉴权机制
- [缓存设计](./caching.md) — cache 模块详细 API
- [邮件服务](./mail.md) — mail 模块详细配置
- [数据库设计](./database.md) — 各模块对应的表结构
