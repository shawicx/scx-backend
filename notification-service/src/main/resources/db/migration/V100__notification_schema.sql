-- ============================================================
-- SCX Backend 通知域表（渠道 / 消息 / 用户投递）
-- 归属 notification-service，版本段 100-199（V1 属 rbac/file，
-- V2/V3 属 common-audit；identity classpath 同时解析多份迁移，
-- 版本号不可冲突，详见 docs/superpowers/specs/2026-09-19-notification-design.md）
-- ============================================================

-- 消息渠道（业务场景分类 + 投递类型）
CREATE TABLE notification_channels (
    id TEXT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    "isSystem" BOOLEAN NOT NULL DEFAULT FALSE,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT notification_channels_pkey PRIMARY KEY (id),
    CONSTRAINT notification_channels_code_key UNIQUE (code)
);
CREATE INDEX idx_notification_channels_type ON notification_channels (type);

-- 消息主表（targetType: ALL/USERS/ROLES 广播与定向；DIRECT 为事务邮件记录）
CREATE TABLE notifications (
    id TEXT NOT NULL,
    "channelId" CHAR(26) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    link VARCHAR(200),
    level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    targetType VARCHAR(20) NOT NULL,
    targetIds TEXT,
    "recipientEmail" VARCHAR(100),
    "recipientCount" INTEGER NOT NULL DEFAULT 0,
    "senderId" CHAR(26),
    "createdAt" TIMESTAMP NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_channel_fk FOREIGN KEY ("channelId")
        REFERENCES notification_channels(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_notifications_channel ON notifications ("channelId");
CREATE INDEX idx_notifications_created ON notifications ("createdAt");

-- 用户投递记录（写扩散：每收件人一行；readAt 为空即未读）
-- "userId" 逻辑引用 users 表（属 identity-service），仅 DDL 外键不建 JPA 实体
CREATE TABLE user_notifications (
    id TEXT NOT NULL,
    "notificationId" CHAR(26) NOT NULL,
    "userId" CHAR(26) NOT NULL,
    "readAt" TIMESTAMP,
    CONSTRAINT user_notifications_pkey PRIMARY KEY (id),
    CONSTRAINT user_notifications_notification_fk FOREIGN KEY ("notificationId")
        REFERENCES notifications(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT user_notifications_user_fk FOREIGN KEY ("userId")
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT user_notifications_user_notification_key UNIQUE ("userId", "notificationId")
);
CREATE INDEX idx_user_notifications_user_read ON user_notifications ("userId", "readAt");
CREATE INDEX idx_user_notifications_notification ON user_notifications ("notificationId");
