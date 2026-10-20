package com.example.kb.review;
import java.util.List;

/**
 * 选择题
 * @param stem 题干
 * @param options 4 个选项
 * @param correctIndex 正确答案是第几个（0~3）
 * @param explanation 解析：为什么选它
 */
public record ChoiceQuestion(
        String stem,          // 题干
        List<String> options, // 4 个选项
        int correctIndex,     // 正确答案是第几个（0~3）
        String explanation    // 解析：为什么选它
) {}

