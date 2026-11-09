package com.example.kb.service.dto;

/**
 * AI 编辑知识卡时允许修改的字段。
 * 个人备注、评分、来源、状态和归属用户始终由原卡片保留。
 */
public record KnowledgeAiEditResult(
        String title,
        String content,
        String summary,
        String category,
        String tags
) {
}
