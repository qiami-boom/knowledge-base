package com.example.kb.common;

import java.time.LocalDateTime;
/**
 * 时间戳约定：凡是需要在插入时自动填充时间字段的实体都实现它。
 *
 * 实现类在 stamp() 里把 now 写进自己的时间字段：
 * createdAt 已有值时不覆盖（更新场景只刷新 updatedAt）。
 *
 * 存在的理由：让切面只认接口、不认识具体实体类型，
 * 新增实体时 TimestampAspect 一行都不用改。
 */
public interface Timestamped {
    void stamp(LocalDateTime now);
}
