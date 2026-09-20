-- ============================================================
-- 文件分片上传：上传会话表 / 分片记录表 + files 表调整
-- 归属说明：file_upload_sessions、file_upload_parts、files 表均属
-- file-service，但迁移放在 common-audit 共享模块——共享库
-- flyway_schema_history 下所有跑 Flyway 的服务（identity/rbac/file）
-- 必须能解析到同一份版本文件，校验和才一致（与 V2/V3 相同约束）。
-- 说明：
--   - files.size 迁移为 BIGINT 以支持 2GB 以上大文件
--   - files."fileHash"（SHA-256）用于同用户秒传命中
--   - 会话状态：UPLOADING / COMPLETED / ABORTED / EXPIRED（应用层枚举）
-- ============================================================

-- 上传会话表
CREATE TABLE file_upload_sessions (
    id TEXT NOT NULL,
    "userId" CHAR(26) NOT NULL,
    "originalName" VARCHAR(255) NOT NULL,
    "mimeType" VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    "chunkSize" INTEGER NOT NULL,
    "totalChunks" INTEGER NOT NULL,
    "fileHash" VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    "objectKey" VARCHAR(500) NOT NULL,
    "minioUploadId" VARCHAR(255) NOT NULL,
    "fileId" TEXT,
    "expiresAt" TIMESTAMP(6) NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT file_upload_sessions_pkey PRIMARY KEY (id)
);

-- 索引：断点续传按（用户, 哈希, 状态）匹配未完成会话
CREATE INDEX file_upload_sessions_userId_fileHash_status_idx
    ON file_upload_sessions ("userId", "fileHash", status);
-- 索引：过期清理任务扫描
CREATE INDEX file_upload_sessions_status_expiresAt_idx
    ON file_upload_sessions (status, "expiresAt");

-- 分片记录表（复合主键：会话 ID + 分片号）
CREATE TABLE file_upload_parts (
    "uploadId" TEXT NOT NULL,
    "partNumber" INTEGER NOT NULL,
    etag VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    "uploadedAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT file_upload_parts_pkey PRIMARY KEY ("uploadId", "partNumber"),
    CONSTRAINT file_upload_parts_uploadId_fkey FOREIGN KEY ("uploadId")
        REFERENCES file_upload_sessions(id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- files 表：支持大文件与秒传（SET DATA TYPE 为 PG 与 H2(PostgreSQL 模式) 双兼容语法）
ALTER TABLE files ALTER COLUMN size SET DATA TYPE BIGINT;
ALTER TABLE files ADD COLUMN "fileHash" VARCHAR(64);
CREATE INDEX files_userId_fileHash_idx ON files ("userId", "fileHash");
