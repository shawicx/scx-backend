-- ============================================================
-- users 表新增头像列
-- 归属说明：users 表属 identity-service，但迁移放在 common-audit
-- 共享模块——共享库 flyway_schema_history 下所有跑 Flyway 的服务
-- （identity/rbac/file）必须能解析到同一份版本文件，校验和才一致
-- （与 V2 相同的约束，详见 V2 文件头注释）。
-- avatar 存文件服务 files.id 引用；私有桶，展示直链由前端经
-- /files/info 换取预签名 URL。
-- ============================================================

ALTER TABLE users ADD COLUMN "avatar" TEXT;
