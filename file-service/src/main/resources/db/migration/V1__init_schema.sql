-- ============================================================
-- SCX Backend 初始 schema
-- 移植自 scx-service: prisma/migrations/20260526112459_init/migration.sql
-- 排除 ai_requests 表（AI 相关，不在本项目范围）
-- ============================================================

-- 用户表
CREATE TABLE users (
    id TEXT NOT NULL,
    email VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    "emailVerified" BOOLEAN NOT NULL DEFAULT false,
    "emailVerificationCode" VARCHAR(6),
    "emailVerificationExpiry" TIMESTAMP,
    preferences JSONB,
    "lastLoginIp" VARCHAR(45),
    "lastLoginAt" TIMESTAMP,
    "loginCount" INTEGER NOT NULL DEFAULT 1,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP NOT NULL,
    "deletedAt" TIMESTAMP,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

-- 角色表
CREATE TABLE roles (
    id TEXT NOT NULL,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT roles_pkey PRIMARY KEY (id)
);

-- 权限表（树形自引用）
CREATE TABLE permissions (
    id TEXT NOT NULL,
    name VARCHAR(100) NOT NULL,
    action VARCHAR(50),
    resource VARCHAR(100),
    description VARCHAR(255),
    type VARCHAR(20) NOT NULL DEFAULT 'BUTTON',
    "parentId" CHAR(26),
    level INTEGER NOT NULL DEFAULT 0,
    path VARCHAR(200),
    icon VARCHAR(100),
    sort INTEGER NOT NULL DEFAULT 0,
    visible SMALLINT NOT NULL DEFAULT 1,
    status SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT permissions_pkey PRIMARY KEY (id)
);

-- 用户-角色关联表
CREATE TABLE user_roles (
    id TEXT NOT NULL,
    "userId" CHAR(26) NOT NULL,
    "roleId" CHAR(26) NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_roles_pkey PRIMARY KEY (id)
);

-- 角色-权限关联表
CREATE TABLE role_permissions (
    id TEXT NOT NULL,
    "roleId" CHAR(26) NOT NULL,
    "permissionId" CHAR(26) NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT role_permissions_pkey PRIMARY KEY (id)
);

-- 文件表
CREATE TABLE files (
    id TEXT NOT NULL,
    "userId" CHAR(26) NOT NULL,
    "originalName" VARCHAR(255) NOT NULL,
    "mimeType" VARCHAR(100) NOT NULL,
    size INTEGER NOT NULL,
    path VARCHAR(500) NOT NULL,
    url VARCHAR(500) NOT NULL,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deletedAt" TIMESTAMP,
    CONSTRAINT files_pkey PRIMARY KEY (id)
);

-- 唯一索引
CREATE UNIQUE INDEX users_email_key ON users(email);
CREATE UNIQUE INDEX roles_name_key ON roles(name);
CREATE UNIQUE INDEX roles_code_key ON roles(code);
CREATE UNIQUE INDEX permissions_name_key ON permissions(name);
CREATE UNIQUE INDEX user_roles_userId_roleId_key ON user_roles("userId", "roleId");
CREATE UNIQUE INDEX role_permissions_roleId_permissionId_key ON role_permissions("roleId", "permissionId");

-- 普通索引
CREATE INDEX users_email_idx ON users(email);
CREATE INDEX users_isActive_idx ON users("isActive");
CREATE INDEX users_lastLoginIp_idx ON users("lastLoginIp");
CREATE INDEX permissions_parentId_idx ON permissions("parentId");
CREATE INDEX permissions_level_idx ON permissions(level);
CREATE INDEX files_userId_idx ON files("userId");
CREATE INDEX files_mimeType_idx ON files("mimeType");
CREATE INDEX files_createdAt_idx ON files("createdAt");

-- 外键（均 ON DELETE CASCADE）
ALTER TABLE permissions ADD CONSTRAINT permissions_parentId_fkey
    FOREIGN KEY ("parentId") REFERENCES permissions(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT user_roles_userId_fkey
    FOREIGN KEY ("userId") REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT user_roles_roleId_fkey
    FOREIGN KEY ("roleId") REFERENCES roles(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_roleId_fkey
    FOREIGN KEY ("roleId") REFERENCES roles(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_permissionId_fkey
    FOREIGN KEY ("permissionId") REFERENCES permissions(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE files ADD CONSTRAINT files_userId_fkey
    FOREIGN KEY ("userId") REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE;
