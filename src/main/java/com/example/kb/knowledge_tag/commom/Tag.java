package com.example.kb.knowledge_tag.commom;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * tag实体
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tag {
    private Long    id;

    private String  name;          // 名称
    private Long    categoryId;    // 分类
    private String  description;   // 描述
    private String  normalized;    // 规范化名，唯一键
    private Integer usageCount;    // 被引用次数
    private LocalDateTime createdAt; // 创建时间
}
