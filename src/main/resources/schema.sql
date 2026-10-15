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