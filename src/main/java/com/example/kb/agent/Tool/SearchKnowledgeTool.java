package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.entity.Knowledge;
import com.example.kb.mapper.KnowledgeMapper;
import com.example.kb.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 工具：按关键词搜索知识库
 *
 * 只读。它让模型能回答"我以前学过什么"，而不是只能看到当前对话。
 * 返回固定最多 5 条，条数不给模型决定——给多了会装满上下文，反而抓不住重点。
 */
@Slf4j
@Component
public class SearchKnowledgeTool implements AgentTool {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int LIMIT = 5;
    private static final int SNIPPET_LENGTH = 80;

    @Autowired
    private KnowledgeService knowledgeService;

    @Override
    public String name(){
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "按关键词搜索用户已入库的知识，返回匹配的标题和内容摘要。"
                + "当用户问'我之前记过什么/我以前学过 X 吗/库里有没有关于 Y 的'时使用。"
                + "不要用于：整理新内容、查看复习计划、闲聊。";
    }

    @Override
    public Map<String, Object> parameters(){
        return  Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "搜索关键词，通常是技术名词、类名或概念名，如 MyBatis、索引、多态"
                        )
                ),
                "required", List.of("keyword")
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, Long userId) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            //取关键词字段,转string字符串,去掉首尾空格
            String keyword = args.path("keyword").asText("").trim();

            if (keyword.isBlank()) {
                return ToolResult.text("没有收到搜索关键词。");
            }

            List<Knowledge> list = knowledgeService.search(keyword, LIMIT, userId);
            if (list.isEmpty()) {
                return ToolResult.text("没找到和「" + keyword + "」相关的知识。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(list.size()).append(" 条相关知识：\n");
            for (int i = 0; i < list.size(); i++) {
                Knowledge k = list.get(i);
                sb.append(i + 1).append(". [id=").append(k.getId()).append("] ")
                        .append(k.getTitle())
                        .append("（").append(k.getAiScore()).append(" 星）");

                String text = k.getSummary();
                if (text == null || text.isBlank()) {
                    text = k.getContent();
                }
                if (text != null && !text.isBlank()) {
                    String snippet = text.length() > SNIPPET_LENGTH
                            ? text.substring(0, SNIPPET_LENGTH) + "…"
                            : text;
                    sb.append("：").append(snippet.replace("\n", " "));
                }
            }
            sb.append("可以据此回答用户，或建议他打开某一条看详情。");

            return ToolResult.text(sb.toString());

        } catch (Exception e) {
            log.warn("[工具] search_knowledge 执行失败：{}", e.getMessage());
            return ToolResult.text("搜索失败：" + e.getMessage());
        }
    }

}
