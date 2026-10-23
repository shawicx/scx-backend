-- ============================================================
-- SCX Backend AI 域多会话改造
-- 归属 ai-service（V200 建 ai_model_configs / ai_messages）。
-- 新增 ai_sessions 会话表；ai_messages 增加 "sessionId" 并回填：
-- 存量消息按用户归入自动生成的"历史对话"会话。
-- ============================================================

-- 会话表（按 "userId" + "updatedAt" 倒序即会话列表）
CREATE TABLE ai_sessions (
    id TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    title VARCHAR(100) NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT ai_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT ai_sessions_user_fk FOREIGN KEY ("userId")
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_ai_sessions_user_updated ON ai_sessions ("userId", "updatedAt");

-- 消息表挂到会话（先可空，回填后收紧）
ALTER TABLE ai_messages ADD COLUMN "sessionId" TEXT;

-- 为有存量消息的每个用户生成一个"历史对话"会话（id 取随机 md5 前 26 位，对齐 ULID 长度）
INSERT INTO ai_sessions (id, "userId", title, "createdAt", "updatedAt")
SELECT substr(md5(random()::text) || md5(random()::text), 1, 26), "userId", '历史对话', MIN("createdAt"), MAX("createdAt")
FROM ai_messages
GROUP BY "userId";

UPDATE ai_messages m
SET "sessionId" = s.id
FROM ai_sessions s
WHERE s."userId" = m."userId";

ALTER TABLE ai_messages ALTER COLUMN "sessionId" SET NOT NULL;
ALTER TABLE ai_messages ADD CONSTRAINT ai_messages_session_fk FOREIGN KEY ("sessionId")
    REFERENCES ai_sessions(id) ON DELETE CASCADE ON UPDATE CASCADE;
CREATE INDEX idx_ai_messages_session ON ai_messages ("sessionId", "createdAt");
