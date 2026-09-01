-- ============================================================
-- SCX Backend 审计日志表（操作日志 + 登录日志）
-- 归属 common-audit 共享模块：凡依赖该模块的服务（identity/rbac/file）
-- 均从 classpath 解析到本文件，共享库下校验和一致。
-- ============================================================

-- 登录日志表（登录成功/失败、登出、刷新令牌的逐次记录）
CREATE TABLE login_logs (
    id TEXT NOT NULL,
    "userId" CHAR(26),
    "email" VARCHAR(100),
    "loginType" VARCHAR(32) NOT NULL,
    "success" BOOLEAN NOT NULL,
    "failReason" VARCHAR(255),
    "ip" VARCHAR(45),
    "userAgent" VARCHAR(512),
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT login_logs_pkey PRIMARY KEY (id)
);

-- 操作日志表（管理端写操作审计：操作人、模块动作、请求上下文、结果与耗时）
CREATE TABLE operation_logs (
    id TEXT NOT NULL,
    "userId" CHAR(26),
    "userEmail" VARCHAR(100),
    "module" VARCHAR(64) NOT NULL,
    "action" VARCHAR(64) NOT NULL,
    "httpMethod" VARCHAR(8),
    "uri" VARCHAR(512),
    "ip" VARCHAR(45),
    "userAgent" VARCHAR(512),
    "params" VARCHAR(2000),
    "success" BOOLEAN NOT NULL,
    "errorMessage" VARCHAR(512),
    "costMs" BIGINT NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operation_logs_pkey PRIMARY KEY (id)
);

-- 普通索引
CREATE INDEX login_logs_userId_idx ON login_logs ("userId");
CREATE INDEX login_logs_email_idx ON login_logs ("email");
CREATE INDEX login_logs_createdAt_idx ON login_logs ("createdAt");
CREATE INDEX operation_logs_userId_idx ON operation_logs ("userId");
CREATE INDEX operation_logs_module_idx ON operation_logs ("module");
CREATE INDEX operation_logs_createdAt_idx ON operation_logs ("createdAt");
