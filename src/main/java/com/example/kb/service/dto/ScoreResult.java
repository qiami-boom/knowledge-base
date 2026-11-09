package com.example.kb.service.dto;

import java.util.List;

public record ScoreResult(String category, int score, String title, String reason,
                          boolean why, boolean general, String summary, List<String> tags) {

    // 兼容旧调用：AI 调用失败、格式错误这些场景拿不到两个布尔
    public ScoreResult(String category, int score, String title, String reason) {
        this(category, score, title, reason, false, false, "", List.of());
    }

    public ScoreResult(String category, int score, String title, String reason,
                       boolean why, boolean general, String summary) {
        this(category, score, title, reason, why, general, summary, List.of());
    }

    // 两个是非题映射成分数，不再让模型自己填分
    public static int scoreOf(boolean why, boolean general){
        if(why && general){
            return 5;
        }
        if(why){
            return 4;
        }
        if(general){
            return 3;
        }
        return 2;
    }
}
