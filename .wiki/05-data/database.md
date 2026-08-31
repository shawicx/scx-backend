# 数据库设计

## 概览

- **数据库**：PostgreSQL 16，单库 `scx-backend`，共享库逻辑隔离（identity 只访问 users / user_roles；rbac 访问 roles / role_permissions / permissions；file 访问 files）
- **Schema 管理**：Flyway（`ddl-auto=validate` 仅校验，不自动建表）
- **主键策略**：ULID（26 字符 Crockford Base32，应用层生成）
- **时区**：`hibernate.jdbc.time_zone=UTC`
- 连接配置：`jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_DATABASE:scx-backend}`（各服务 `application.yml`）

## 迁移文件位置（微服务拆分后的变化）

⚠️ 与单体时代不同，`V1__init_schema.sql` 现有**两份内容完全相同的副本**：

- `rbac-service/src/main/resources/db/migration/V1__init_schema.sql`
- `file-service/src/main/resources/db/migration/V1__init_schema.sql`

- **identity-service 自身没有 db/migration 目录**：它通过 Gradle 依赖 `project(":rbac-service")`（过渡期进程内耦合）把 rbac 的 V1 带上了 classpath，Flyway 照常执行。
- **notification-service 无数据库、无 Flyway**。
- 文件头注明 schema 移植自 scx-service 的 Prisma 迁移（排除 AI 相关的 `ai_requests` 表）。
- **新增迁移时的注意点**：rbac 与 file 各自维护独立的 `flyway_schema_history`。涉及同一物理库的变更时，按表的归属在对应服务添加 `V{N}__xxx.sql`；两边都涉及的变更需保证最终 schema 一致（待确认：是否收敛为单一迁移属主）。

## 表结构

共 6 张表，由 `V1__init_schema.sql` 创建。

### users — 用户（identity）

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱 |
| name | VARCHAR(50) | NOT NULL | 用户名 |
| password | VARCHAR(255) | NOT NULL | BCrypt 哈希 |
| "emailVerified" | BOOLEAN | NOT NULL DEFAULT false | 邮箱是否验证 |
| "emailVerificationCode" | VARCHAR(6) | | 验证码 |
| "emailVerificationExpiry" | TIMESTAMP | | 验证码过期时间 |
| preferences | JSONB | | 偏好设置（实体侧为 `UserPreferences` Embeddable） |
| "lastLoginIp" | VARCHAR(45) | | 最后登录 IP |
| "lastLoginAt" | TIMESTAMP | | 最后登录时间 |
| "loginCount" | INTEGER | NOT NULL DEFAULT 1 | 登录次数 |
| "isActive" | BOOLEAN | NOT NULL DEFAULT true | 是否启用 |
| "createdAt" | TIMESTAMP | NOT NULL DEFAULT now() | 创建时间 |
| "updatedAt" | TIMESTAMP | NOT NULL | 更新时间 |
| "deletedAt" | TIMESTAMP | | 软删除标记 |

**索引**：`email`（唯一 + 普通）、`"isActive"`、`"lastLoginIp"`

### roles — 角色（rbac）

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| name | VARCHAR(50) | NOT NULL, UNIQUE | 角色名 |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 角色编码（如 `SUPER_ADMIN`） |
| description | VARCHAR(255) | | 描述 |
| "isSystem" | BOOLEAN | NOT NULL DEFAULT false | 系统内置（不可删） |
| "createdAt" / "updatedAt" | TIMESTAMP(6) | NOT NULL | |

### permissions — 权限，树形自引用（rbac）

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 权限名 |
| action | VARCHAR(50) | | 动作（按钮类型） |
| resource | VARCHAR(100) | | 资源（按钮类型） |
| description | VARCHAR(255) | | 描述 |
| type | VARCHAR(20) | NOT NULL DEFAULT 'BUTTON' | `MENU` / `BUTTON` |
| "parentId" | CHAR(26) | 自引用 FK | 父权限 ID |
| level | INTEGER | NOT NULL DEFAULT 0 | 层级（1/2/3） |
| path | VARCHAR(200) | | 路由路径（菜单） |
| icon | VARCHAR(100) | | 图标（菜单） |
| sort | INTEGER | NOT NULL DEFAULT 0 | 排序号 |
| visible | SMALLINT | NOT NULL DEFAULT 1 | 是否可见 |
| status | SMALLINT | NOT NULL DEFAULT 1 | 状态 |
| "createdAt" / "updatedAt" | TIMESTAMP(6) | | |

**索引**：`"parentId"`、`level`。自引用外键 `ON DELETE CASCADE`（删父级联删子节点）。

### user_roles — 用户-角色关联

| 列名 | 类型 | 约束 |
| --- | --- | --- |
| id | TEXT | PK |
| "userId" | CHAR(26) | NOT NULL, FK → users(id) |
| "roleId" | CHAR(26) | NOT NULL, FK → roles(id) |
| "createdAt" | TIMESTAMP(6) | |

**唯一约束**：`("userId", "roleId")`

### role_permissions — 角色-权限关联

| 列名 | 类型 | 约束 |
| --- | --- | --- |
| id | TEXT | PK |
| "roleId" | CHAR(26) | NOT NULL, FK → roles(id) |
| "permissionId" | CHAR(26) | NOT NULL, FK → permissions(id) |
| "createdAt" | TIMESTAMP(6) | |

**唯一约束**：`("roleId", "permissionId")`

### files — 文件（file）

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | ULID |
| "userId" | CHAR(26) | NOT NULL, FK → users(id) | 所属用户 |
| "originalName" | VARCHAR(255) | NOT NULL | 原始文件名 |
| "mimeType" | VARCHAR(100) | NOT NULL | MIME 类型 |
| size | INTEGER | NOT NULL | 大小（字节） |
| path | VARCHAR(500) | NOT NULL | 对象键（MinIO 存储路径） |
| url | VARCHAR(500) | NOT NULL | 访问 URL（逻辑地址） |
| "createdAt" | TIMESTAMP | | |
| "deletedAt" | TIMESTAMP | | 软删除 |

**索引**：`"userId"`、`"mimeType"`、`"createdAt"`

## ER 关系图

```
┌──────────┐     ┌────────────┐     ┌──────────┐
│  users   │◄────│ user_roles │────►│  roles   │
│(identity)│ 1:N │  (identity)│ N:1 │ (rbac)   │
└──────────┘     └────────────┘     └────┬─────┘
     ▲                                   │ 1:N
     │ 1:N                      ┌────────────────────┐
     │                          │ role_permissions   │
     │                          │      (rbac)        │
┌────┴─────┐                    └─────────┬──────────┘
│  files   │                             │ N:1
│  (file)  │                   ┌─────────▼──────────┐
└──────────┘                   │    permissions     │
                               │ (rbac，自引用树)   │
                               └────────────────────┘
```

- 用户 ↔ 角色：多对多（`user_roles`）；角色 ↔ 权限：多对多（`role_permissions`）；权限自引用树；用户 → 文件一对多
- 各服务通过共享同一物理库 + 按表归属读写实现逻辑隔离（过渡期 identity 直连 rbac 的表）

## 外键策略

所有外键 `ON DELETE CASCADE ON UPDATE CASCADE`（物理删除才触发；应用层的删除是软删除——users / files 置 `deletedAt`）。

## ULID 主键策略

由 `common/src/main/kotlin/com/scx/backend/common/util/IdGenerator.kt` 的 `nextId()` 生成：

- 26 字符 Crockford Base32（编码表排除 `I/L/O/U`）
- 结构：`[时间戳 10 字符（48 位毫秒）][随机 16 字符（80 位）]`——字典序即时间序，利于索引
- 应用层生成，不依赖数据库自增/序列，分布式友好

## 列名约定

列名采用**双引号包裹的驼峰命名**（如 `"emailVerified"`），双引号使 PostgreSQL 区分大小写、避免折叠为小写；JPA 实体用 `@Column(name = "\"emailVerified\"")` 映射。单字列名（`email`、`name`）无需双引号。

## Flyway 迁移规范

### 命名

```
V{版本号}__{描述}.sql     # 例：V1__init_schema.sql、V2__add_user_avatar.sql
```

### 关键规则

⚠️ **已应用的 migration 文件内容不可修改**（包括注释、空格、换行）。Flyway 首次应用时计算 checksum 记入 `flyway_schema_history`，启动时比对，不一致即报错：

```
Migration checksum mismatch for migration version 1
```

- 修改 schema → 新建 `V{N}__xxx.sql`
- 开发库重置 → 删库重建或 `flyway repair`（慎用）

### 配置（identity / rbac / file 一致）

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

Hibernate `ddl-auto=validate` 校验实体与表结构一致，实体与迁移必须同步维护。

## 相关文档

- [module-map](../03-codebase/module-map.md) — 各表对应的业务模块
- [configuration](../06-configuration/configuration.md) — 数据库连接环境变量
- [development-guide](../08-development/development-guide.md) — 如何新增表与实体
