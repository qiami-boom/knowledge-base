package com.example.kb.service.dto;

/**
 * AI 精简保存方式。keepOriginal=true 时保留原文，并把精简版放在正文上方。
 */
public record CondenseKnowledgeRequest(Boolean keepOriginal) {

    public boolean shouldKeepOriginal() {
        return Boolean.TRUE.equals(keepOriginal);
    }
}
