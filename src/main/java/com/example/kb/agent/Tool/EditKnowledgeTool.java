package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.entity.Knowledge;
import com.example.kb.service.KnowledgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具：根据用户指令编辑一张已经入库的知识卡。
 */
@Slf4j
@Component
public class EditKnowledgeTool implements AgentTool {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "edit_knowledge";
    }

    @Override
    public String description() {
        return "根据明确的编辑指令修改一张已入库知识卡，可精简、改写、纠正标题和正文。"
                + "只有用户明确要求修改已有知识时才能使用。"
                + "id 必须先通过 search_knowledge 或 read_knowledge 确认，不要猜。"
                + "该工具只修改标题、正文、摘要、分类和标签，不会改个人备注、评分、来源和归属用户。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "knowledgeId", Map.of(
                                "type", "integer",
                                "description", "要编辑的知识 id，必须来自搜索结果"
                        ),
                        "instruction", Map.of(
                                "type", "string",
                                "description", "用户要求的编辑指令，例如：精简正文并保留代码；把标题改成更具体的说法"
                        )
                ),
                "required", List.of("knowledgeId", "instruction")
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, Long userId) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            long knowledgeId = args.path("knowledgeId").asLong(0);
            String instruction = args.path("instruction").asText("").trim();

            if (knowledgeId <= 0) {
                return ToolResult.text("没有拿到有效的知识 id。");
            }
            if (instruction.isBlank()) {
                return ToolResult.text("没有拿到明确的编辑指令。");
            }

            Knowledge updated = knowledgeService.aiEdit(knowledgeId, instruction, userId);
            return ToolResult.text(
                    "已编辑知识卡 [id=" + updated.getId() + "]。\n"
                            + "新标题：" + updated.getTitle() + "\n"
                            + "新摘要：" + (updated.getSummary() == null ? "无" : updated.getSummary()) + "\n"
                            + "个人备注、评分、来源和归属用户均未改变。"
            );
        } catch (Exception e) {
            log.warn("[工具] edit_knowledge 执行失败：{}", e.getMessage());
            return ToolResult.text("编辑知识卡失败：" + e.getMessage() + "。请先确认知识 id，再重试。");
        }
    }
}
