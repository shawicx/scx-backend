-- ============================================================
-- 数据字典：字典类型 + 字典数据
-- ============================================================

-- 字典类型表
CREATE TABLE dict_type (
    id TEXT NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    status SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT dict_type_pkey PRIMARY KEY (id),
    CONSTRAINT dict_type_name_key UNIQUE (name),
    CONSTRAINT dict_type_code_key UNIQUE (code)
);

-- 字典数据表（typeId 外键级联删除：删类型自动删数据）
CREATE TABLE dict_data (
    id TEXT NOT NULL,
    "typeId" CHAR(26) NOT NULL,
    label VARCHAR(100) NOT NULL,
    value VARCHAR(100) NOT NULL,
    sort INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,
    CONSTRAINT dict_data_pkey PRIMARY KEY (id),
    CONSTRAINT dict_data_type_value_key UNIQUE ("typeId", value),
    CONSTRAINT dict_data_type_id_fkey FOREIGN KEY ("typeId") REFERENCES dict_type (id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX dict_data_type_id_idx ON dict_data ("typeId");
