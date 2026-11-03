package com.example.kb.service.dto;

public record ScoreResult(String category, int score, String title, String reason,
                          boolean why, boolean general) {

    // 兼容旧调用：AI 调用失败、格式错误这些场景拿不到两个布尔
    public ScoreResult(String category, int score, String title, String reason) {
        this(category, score, title, reason, false, false);
    }
}