-- ============================================================
-- SCX Backend AI 域表（模型配置 / 会话消息）
-- 归属 ai-service，版本段 200+（V1 属 rbac/file，V2-V6 属 common-audit，
-- V100-V199 属 notification；单库多服务版本号不可冲突）
-- ============================================================

-- 用户 AI 模型配置（每用户一条，OpenAI 兼容协议：baseUrl + model + apiKey）
-- apiKey 为 AES-256-GCM 密文，接口永不返回明文
CREATE TABLE ai_model_configs (
    id TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "baseUrl" TEXT NOT NULL,
    "apiKey" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "systemPrompt" TEXT,
    "temperature" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT ai_model_configs_pkey PRIMARY KEY (id),
    CONSTRAINT ai_model_configs_user_fk FOREIGN KEY ("userId")
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ai_model_configs_user_key UNIQUE ("userId")
);

-- 会话消息（初版单会话：按 "userId" + "createdAt" 时间序即完整会话）
-- "userId" 逻辑引用 users 表（属 identity-service），仅 DDL 外键不建 JPA 实体
CREATE TABLE ai_messages (
    id TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT ai_messages_pkey PRIMARY KEY (id),
    CONSTRAINT ai_messages_user_fk FOREIGN KEY ("userId")
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_ai_messages_user_created ON ai_messages ("userId", "createdAt");
