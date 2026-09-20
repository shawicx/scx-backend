-- ============================================================
-- 数据权限：角色表新增 dataScope 列
-- 归属说明：roles 表属 rbac-service，但迁移放在 common-audit
-- 共享模块——共享库 flyway_schema_history 下所有跑 Flyway 的
-- 服务（identity/rbac/file）必须能解析到同一份版本文件，校验和
-- 才一致（与 V2/V3/V4 相同约束）。
-- 说明：
--   - 取值 ALL / SELF；DEPT / DEPT_AND_CHILD / CUSTOM 为部门体系
--     预留档位（角色配置接口当前会拒绝，写入仅经 DB 直改）
--   - 存量角色一律默认 SELF；SUPER_ADMIN 置 ALL（隐含全部数据可见）
-- ============================================================

ALTER TABLE roles ADD COLUMN "dataScope" VARCHAR(20) NOT NULL DEFAULT 'SELF';

UPDATE roles SET "dataScope" = 'ALL' WHERE code = 'SUPER_ADMIN';
