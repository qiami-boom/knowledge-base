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