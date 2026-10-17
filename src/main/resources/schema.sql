-- 个人知识库主表
CREATE TABLE IF NOT EXISTS knowledge (
                                         id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                         title         TEXT NOT NULL,
                                         content       TEXT,
                                         source        TEXT,
                                         category      TEXT,
                                         tags          TEXT,
                                         star_rating   INTEGER,
                                         ai_score      INTEGER,
                                         personal_note TEXT,
                                         created_at    TEXT NOT NULL,
                                         updated_at    TEXT NOT NULL
);

-- 复习卡片表：间隔重复（spaced repetition）调度的核心表
-- knowledge (1) ──────< review_card (N)：一条知识可蒸馏多张卡
CREATE TABLE IF NOT EXISTS review_card (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    knowledge_id     INTEGER NOT NULL,          -- 逻辑外键 → knowledge.id（SQLite 外键需 PRAGMA 开启，先靠 service 层保证）
    question         TEXT    NOT NULL,          
    answer           TEXT    NOT NULL,          -- 复习答案：翻面才看
    stage            INTEGER NOT NULL DEFAULT 1, -- 当前阶梯阶数 1~7（1天→2→4→7→15→30→60）
    next_review_at   TEXT    NOT NULL,          -- 下次应复习时间（ISO-8601 文本，索引键）
    last_reviewed_at TEXT,                      -- 最近一次复习时间；NULL = 从未复习过
    created_at       TEXT    NOT NULL           -- 卡片生成时间
);

-- 为最热查询 Q1（查到期）建的索引；IF NOT EXISTS 保证重复启动幂等
CREATE INDEX IF NOT EXISTS idx_review_card_due
    ON review_card(next_review_at);

-- ============ 标签系统：AI 自动打标 + 库内匹配复用 ============

-- 标签主表
-- normalized 是唯一键：规范化后的名称（TagNormalizer 产出），精确匹配靠它
CREATE TABLE IF NOT EXISTS tag (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,               -- 展示名，如 "Spring Boot"
    category_id INTEGER,                        -- 分类 → category.id（预置 10 类，AI 只能选不能建）
    description TEXT,                           -- 描述：AI 生成，用于消歧 + 喂给匹配 Prompt
    normalized  TEXT    NOT NULL,               -- 规范化名，唯一键
    usage_count INTEGER NOT NULL DEFAULT 0,     -- 被引用次数，标签治理用
    created_at  TEXT    NOT NULL                -- 创建时间
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_normalized ON tag(normalized);
CREATE INDEX        IF NOT EXISTS idx_tag_category   ON tag(category_id);

-- 别名表：同义词/简称的永久映射
-- AI 判定成功后把新写法记成别名，下次直接走 L1 精确匹配，不再调 AI
CREATE TABLE IF NOT EXISTS tag_alias (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_id     INTEGER NOT NULL,                -- → tag.id
    alias      TEXT    NOT NULL,
    normalized TEXT    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alias_normalized ON tag_alias(normalized);

-- 知识 ↔ 标签 多对多
-- match_score / match_layer 记录这次匹配的分数与命中层，供覆盖率分析与阈值调优
CREATE TABLE IF NOT EXISTS knowledge_tag (
    knowledge_id INTEGER NOT NULL,              -- → knowledge.id
    tag_id       INTEGER NOT NULL,              -- → tag.id
    match_score  REAL,                          -- 这次匹配得几分
    match_layer  TEXT,                          -- EXACT / ALIAS / AI / CREATED
    PRIMARY KEY (knowledge_id, tag_id)
);