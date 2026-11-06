package com.example.kb.agent;

import com.example.kb.service.dto.ExtractResult;

import java.util.List;

/**
 * 工具执行结果。同一个结果有两种读者，所以有两个字段：
 * text  —— 给模型读，必须是自然语言，模型看不懂结构化对象
 * cards —— 给前端渲染，可为空（不是每个工具都会产出草稿卡片）
 */
public record ToolResult (String text, List<ExtractResult.Item> cards){

    public static ToolResult text(String text){
        return new ToolResult (text, List.of());
    }

    public static ToolResult withCards(String text, List<ExtractResult.Item> cards){
        return new ToolResult(text, cards);
    }
}
