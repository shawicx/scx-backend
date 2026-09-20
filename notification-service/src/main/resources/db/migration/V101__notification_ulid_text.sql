-- ============================================================
-- notification 域 ULID 列类型修正：CHAR(26) → text
-- V100 将 channelId / senderId / notificationId / userId 建为 CHAR(26)（bpchar），
-- 与实体 @Column(length = 30) 的 varchar 映射不符，Hibernate ddl-auto=validate
-- 校验失败；仓库惯例与 users.id 一致（DDL text + 实体 length=30）。
-- V100 已应用、checksum 已固定，故以本迁移修正而非改动 V100。
-- ============================================================

ALTER TABLE notifications ALTER COLUMN "channelId" TYPE text;
ALTER TABLE notifications ALTER COLUMN "senderId" TYPE text;
ALTER TABLE user_notifications ALTER COLUMN "notificationId" TYPE text;
ALTER TABLE user_notifications ALTER COLUMN "userId" TYPE text;
