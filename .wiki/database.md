# 数据库设计

## 概览

- **数据库**：PostgreSQL 16
- **Schema 管理**：Flyway（迁移文件位于 `src/main/resources/db/migration/`）
- **Hibernate 模式**：`ddl-auto=validate`（仅校验实体与 schema 一致，不自动建表）
- **主键策略**：ULID（26 字符 Crockford Base32，应用层生成）
- **时区**：`hibernate.jdbc.time_zone=UTC`

## 表结构

共 6 张表，由 `V1__init_schema.sql` 创建。

### users — 用户

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱 |
| name | VARCHAR(50) | NOT NULL | 用户名 |
| password | VARCHAR(255) | NOT NULL | BCrypt 哈希 |
| "emailVerified" | BOOLEAN | NOT NULL DEFAULT false | 邮箱是否验证 |
| "emailVerificationCode" | VARCHAR(6) | | 验证码 |
| "emailVerificationExpiry" | TIMESTAMP | | 验证码过期时间 |
| preferences | JSONB | | 偏好设置 |
| "lastLoginIp" | VARCHAR(45) | | 最后登录 IP |
| "lastLoginAt" | TIMESTAMP | | 最后登录时间 |
| "loginCount" | INTEGER | NOT NULL DEFAULT 1 | 登录次数 |
| "isActive" | BOOLEAN | NOT NULL DEFAULT true | 是否启用 |
| "createdAt" | TIMESTAMP | NOT NULL DEFAULT now() | 创建时间 |
| "updatedAt" | TIMESTAMP | NOT NULL | 更新时间 |
| "deletedAt" | TIMESTAMP | | 软删除标记 |

**索引**：`email`（唯一 + 普通）、`"isActive"`、`"lastLoginIp"`

### roles — 角色

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| name | VARCHAR(50) | NOT NULL, UNIQUE | 角色名 |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 角色编码（如 `SUPER_ADMIN`） |
| description | VARCHAR(255) | | 描述 |
| "isSystem" | BOOLEAN | NOT NULL DEFAULT false | 是否系统内置（不可删） |
| "createdAt" | TIMESTAMP(6) | NOT NULL DEFAULT now() | |
| "updatedAt" | TIMESTAMP(6) | NOT NULL | |

### permissions — 权限（树形自引用）

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 权限名 |
| action | VARCHAR(50) | | 动作（按钮类型） |
| resource | VARCHAR(100) | | 资源（按钮类型） |
| description | VARCHAR(255) | | 描述 |
| type | VARCHAR(20) | NOT NULL DEFAULT 'BUTTON' | 类型：`MENU` / `BUTTON` |
| "parentId" | CHAR(26) | | 父权限 ID（自引用） |
| level | INTEGER | NOT NULL DEFAULT 0 | 层级（1/2/3） |
| path | VARCHAR(200) | | 路由路径（菜单） |
| icon | VARCHAR(100) | | 图标（菜单） |
| sort | INTEGER | NOT NULL DEFAULT 0 | 排序号 |
| visible | SMALLINT | NOT NULL DEFAULT 1 | 是否可见（0/1） |
| status | SMALLINT | NOT NULL DEFAULT 1 | 状态（0/1） |
| "createdAt" | TIMESTAMP(6) | | |
| "updatedAt" | TIMESTAMP(6) | | |

**索引**：`"parentId"`、`level`

**自引用外键**：`"parentId" → permissions(id) ON DELETE CASCADE`（删除父节点级联删除子节点）

### user_roles — 用户-角色关联

| 列名 | 类型 | 约束 |
| --- | --- | --- |
| id | TEXT | PK |
| "userId" | CHAR(26) | NOT NULL, FK → users(id) |
| "roleId" | CHAR(26) | NOT NULL, FK → roles(id) |
| "createdAt" | TIMESTAMP(6) | |

**唯一约束**：`("userId", "roleId")` — 防止重复分配

### role_permissions — 角色-权限关联

| 列名 | 类型 | 约束 |
| --- | --- | --- |
| id | TEXT | PK |
| "roleId" | CHAR(26) | NOT NULL, FK → roles(id) |
| "permissionId" | CHAR(26) | NOT NULL, FK → permissions(id) |
| "createdAt" | TIMESTAMP(6) | |

**唯一约束**：`("roleId", "permissionId")` — 防止重复分配

### files — 文件

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| "userId" | CHAR(26) | NOT NULL, FK → users(id) | 所属用户 |
| "originalName" | VARCHAR(255) | NOT NULL | 原始文件名 |
| "mimeType" | VARCHAR(100) | NOT NULL | MIME 类型 |
| size | INTEGER | NOT NULL | 大小（字节） |
| path | VARCHAR(500) | NOT NULL | 存储路径 |
| url | VARCHAR(500) | NOT NULL | 访问 URL |
| "createdAt" | TIMESTAMP | | |
| "deletedAt" | TIMESTAMP | | 软删除 |

**索引**：`"userId"`、`"mimeType"`、`"createdAt"`

## ER 关系图

```
┌──────────┐     ┌────────────┐     ┌──────────┐
│  users   │◄────│ user_roles │────►│  roles   │
│          │ 1:N │            │ N:1 │          │
└──────────┘     └────────────┘     └────┬─────┘
     ▲                                   │
     │ 1:N                               │ 1:N
     │                          ┌────────────────────┐
     │                          │ role_permissions   │
     │                          └─────────┬──────────┘
     │                                    │ N:1
     │                          ┌─────────▼──────────┐
     │                          │    permissions     │
     │                          │  (自引用树)         │
┌────┴─────┐                    └────────────────────┘
│  files   │
└──────────┘
```

**关系说明**：

- 用户 ↔ 角色：多对多（通过 `user_roles`）
- 角色 ↔ 权限：多对多（通过 `role_permissions`）
- 权限 ↔ 权限：自引用树（`parentId`，级联删除）
- 用户 → 文件：一对多

## 外键策略

所有外键均为 `ON DELETE CASCADE ON UPDATE CASCADE`：

- 删除用户 → 级联删除其 `user_roles` 和 `files`
- 删除角色 → 级联删除相关的 `user_roles` 和 `role_permissions`
- 删除权限 → 级联删除相关的 `role_permissions` 和子权限（自引用）

> 注意：用户删除在应用层是软删除（设置 `deletedAt`），级联删除仅在物理删除时触发。

## ULID 主键策略

全项目统一使用 ULID 作为实体主键，由 `common/util/IdGenerator.kt` 生成。

### 格式

- 26 字符 Crockford Base32 编码
- 编码表：`0123456789ABCDEFGHJKMNPQRSTVWXYZ`（排除 `I/L/O/U` 避免混淆）

### 结构

```
[时间戳部分 10 字符][随机部分 16 字符]
  ↑ 48 位毫秒时间       ↑ 80 位随机
```

### 特性

- **时间排序**：前 10 字符编码时间戳，按字典序排序即按时间排序，利于数据库索引
- **全局唯一**：80 位随机数，碰撞概率极低
- **URL 安全**：纯字母数字，无特殊字符
- **应用层生成**：不依赖数据库自增或序列，分布式友好

### 使用方式

```kotlin
val user = User(
    id = IdGenerator.nextId(),
    // ...
)
```

所有实体（users / roles / permissions / user_roles / role_permissions / files）的主键均通过 `IdGenerator.nextId()` 赋值。

## 列名约定

数据库列名采用**双引号包裹的驼峰命名**（如 `"emailVerified"`、`"createdAt"`），原因：

- 保留驼峰可读性
- 双引号使 PostgreSQL 区分大小写，避免被折叠为小写
- JPA 实体通过 `@Column(name = "\"emailVerified\"")` 映射

> 单字列名（如 `email`、`name`）无需双引号。

## Flyway 迁移规范

### 迁移文件命名

```
V{版本号}__{描述}.sql
```

示例：`V1__init_schema.sql`、`V2__add_user_avatar.sql`

- 版本号：递增整数或点分版本（`1`、`2`、`2.1`）
- 描述：下划线分隔的小写英文

### 关键规则

⚠️ **已应用的 migration 文件内容不可修改**（包括注释、空格、换行）。

Flyway 在首次应用时计算文件 checksum 并记录到 `flyway_schema_history` 表。后续启动时重新计算 checksum 与记录值比对，不一致则报错：

```
Migration checksum mismatch for migration version 1
-> Applied to database : 954514334
-> Resolved locally    : 1016293965
```

**正确做法**：

- 需要修改 schema → 新建 `V{N}__xxx.sql`，不动旧文件
- 开发环境需要重置 → 删除数据库重建，或执行 `flyway repair`（更新 checksum，慎用）

### 配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # 已有库上首次迁移建立基线
```

### Hibernate 与 Flyway 协作

- Flyway 负责 DDL（建表、改表）
- Hibernate `ddl-auto=validate` 仅校验实体与表结构一致，不修改 schema
- 实体定义必须与 migration 保持同步，否则启动时校验失败

## 相关文档

- [业务模块](./modules.md) — 各模块对应的业务逻辑
- [配置说明](./configuration.md) — 数据库连接配置
- [开发规范](./development-guide.md) — 如何新增表与实体
