-- ============================================================
-- SCX Backend 博客域表（文章 / 分类 / 标签 / 评论 / 互动统计）
-- 归属 blog-service，版本段 300+（V1 属 rbac/file，V2-V6 属 common-audit，
-- V100-V199 属 notification，V200+ 属 ai；单库多服务版本号不可冲突）
-- ============================================================
-- 设计要点：
--  * 主键/外键均为 TEXT（ULID），与全库一致；
--  * "authorId"/"coverFileId" 为跨服务逻辑引用（users/files 表），
--    沿用 users.avatar 先例不建 DDL 外键；
--  * 公开可见性由 (status=1 已发布, visibility=0 公开, "deletedAt" IS NULL,
--    "publishedAt" <= now) 联合判定，索引按该判定组织；
--  * slug 全表唯一：软删除后 slug 保留不复用（v1 决策，避免部分唯一索引的
--    PostgreSQL/H2 双方言兼容问题）；
--  * 统计计数用 BIGINT，阅读/点赞经原子 UPDATE 维护，评论数随审核状态维护。

-- 文章分类（单层，一篇文章最多一个分类）
CREATE TABLE blog_categories (
    id TEXT NOT NULL,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_categories_pkey PRIMARY KEY (id),
    CONSTRAINT blog_categories_slug_key UNIQUE (slug)
);

-- 文章标签（与文章多对多）
CREATE TABLE blog_tags (
    id TEXT NOT NULL,
    name VARCHAR(60) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_tags_pkey PRIMARY KEY (id),
    CONSTRAINT blog_tags_slug_key UNIQUE (slug)
);

-- 文章主体（content 为 Markdown 原文，HTML 渲染由博客前台负责）
-- "categoryId" 用 ON DELETE RESTRICT：有文章的分类禁止直接删除（服务层同样拦截）
CREATE TABLE blog_posts (
    id TEXT NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    summary VARCHAR(500),
    content TEXT NOT NULL,
    "coverFileId" TEXT,
    "categoryId" TEXT,
    "authorId" TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    visibility SMALLINT NOT NULL DEFAULT 0,
    "isTop" BOOLEAN NOT NULL DEFAULT false,
    "allowComment" BOOLEAN NOT NULL DEFAULT true,
    "publishedAt" TIMESTAMP(6),
    "seoTitle" VARCHAR(200),
    "seoDescription" VARCHAR(500),
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    "deletedAt" TIMESTAMP(6),
    CONSTRAINT blog_posts_pkey PRIMARY KEY (id),
    CONSTRAINT blog_posts_slug_key UNIQUE (slug),
    CONSTRAINT blog_posts_category_fk FOREIGN KEY ("categoryId")
        REFERENCES blog_categories(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT blog_posts_author_fk FOREIGN KEY ("authorId")
        REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_blog_posts_public_listing ON blog_posts (status, visibility, "publishedAt");
CREATE INDEX idx_blog_posts_category ON blog_posts ("categoryId", status, "publishedAt");
CREATE INDEX idx_blog_posts_top ON blog_posts ("isTop", "publishedAt");

-- 文章与标签关联（复合主键；两侧删除均级联清理关联）
CREATE TABLE blog_post_tags (
    "postId" TEXT NOT NULL,
    "tagId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_post_tags_pkey PRIMARY KEY ("postId", "tagId"),
    CONSTRAINT blog_post_tags_post_fk FOREIGN KEY ("postId")
        REFERENCES blog_posts(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT blog_post_tags_tag_fk FOREIGN KEY ("tagId")
        REFERENCES blog_tags(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_blog_post_tags_tag ON blog_post_tags ("tagId", "postId");

-- 评论（匿名，先审后显；仅支持一级评论与对一级评论的回复）
-- "visitorKeyHash"/"ipHash" 为脱敏标识，永不进入公开响应
CREATE TABLE blog_comments (
    id TEXT NOT NULL,
    "postId" TEXT NOT NULL,
    "parentId" TEXT,
    "rootId" TEXT,
    nickname VARCHAR(60) NOT NULL,
    email VARCHAR(254),
    website VARCHAR(255),
    content TEXT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    "visitorKeyHash" VARCHAR(128),
    "ipHash" VARCHAR(128),
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    "deletedAt" TIMESTAMP(6),
    CONSTRAINT blog_comments_pkey PRIMARY KEY (id),
    CONSTRAINT blog_comments_post_fk FOREIGN KEY ("postId")
        REFERENCES blog_posts(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT blog_comments_parent_fk FOREIGN KEY ("parentId")
        REFERENCES blog_comments(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT blog_comments_root_fk FOREIGN KEY ("rootId")
        REFERENCES blog_comments(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_blog_comments_listing ON blog_comments ("postId", status, "createdAt");

-- 文章聚合统计（postId 即主键；计数经原子 UPDATE 维护）
CREATE TABLE blog_post_stats (
    "postId" TEXT NOT NULL,
    "viewCount" BIGINT NOT NULL DEFAULT 0,
    "likeCount" BIGINT NOT NULL DEFAULT 0,
    "commentCount" BIGINT NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_post_stats_pkey PRIMARY KEY ("postId"),
    CONSTRAINT blog_post_stats_post_fk FOREIGN KEY ("postId")
        REFERENCES blog_posts(id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- 匿名点赞记录（服务端派生访客标识去重；幂等由唯一约束保证）
CREATE TABLE blog_post_likes (
    id TEXT NOT NULL,
    "postId" TEXT NOT NULL,
    "visitorKeyHash" VARCHAR(128) NOT NULL,
    "createdAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_post_likes_pkey PRIMARY KEY (id),
    CONSTRAINT blog_post_likes_post_fk FOREIGN KEY ("postId")
        REFERENCES blog_posts(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT blog_post_likes_post_visitor_key UNIQUE ("postId", "visitorKeyHash")
);

-- 每日阅读统计（后台"今日阅读/近 7 天趋势"数据源）
CREATE TABLE blog_post_daily_stats (
    "postId" TEXT NOT NULL,
    "statDate" DATE NOT NULL,
    "viewCount" BIGINT NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(6) NOT NULL,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT blog_post_daily_stats_pkey PRIMARY KEY ("postId", "statDate"),
    CONSTRAINT blog_post_daily_stats_post_fk FOREIGN KEY ("postId")
        REFERENCES blog_posts(id) ON DELETE CASCADE ON UPDATE CASCADE
);
