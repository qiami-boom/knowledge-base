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
 * 工具：按 id 读一条知识的全文。
 *
 * search_knowledge 只回标题 + 摘要，用户想"展开看看"时才用这个。
 * id 是模型从搜索结果里拿到的句柄，不是标题——标题可能重名，id 唯一。
 */
@Slf4j
@Component
public class ReadKnowledgeTool implements AgentTool {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 正文最多回 2000 字：够模型判断内容，又不至于把上下文撑爆 */
    private static final int MAX_CONTENT = 2000;

    @Autowired
    private KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "read_knowledge";
    }

    @Override
    public String description() {
        return "按 id 读取一条知识的完整正文。"
                + "当用户想展开看某一条、或搜索结果里的摘要不够判断时要看全文。"
                + "id 必须来自 search_knowledge 返回结果里的 [id=数字]，不要自己编。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "integer",
                                "description", "知识 id，从 search_knowledge 结果里 [id=数字] 那段取"
                        )
                ),
                "required", List.of("id")
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, Long userId) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            long id = args.path("id").asLong(0);
            if (id <= 0) {
                return ToolResult.text("没有拿到有效的知识 id。");
            }

            // 找不到或不属于当前用户时 findById 抛 404，下面统一转成文本
            Knowledge k = knowledgeService.findById(id, userId);

            // 草稿是"用户还没确认"的内容，不能进 Agent 的读取范围：
            // 搜索只回 ACTIVE，这里再挡一道，避免模型拿到 id 就把未确认内容当事实讲出去
            if (!"ACTIVE".equals(k.getStatus())) {
                return ToolResult.text("这条还是未确认的草稿，要让用户先在对话里点「入库」，确认后才能读全文。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("标题：").append(k.getTitle()).append("\n");
            if (k.getSummary() != null && !k.getSummary().isBlank()) {
                sb.append("摘要：").append(k.getSummary()).append("\n");
            }
            sb.append("星级：").append(k.getAiScore()).append("\n\n");
            sb.append("正文：\n");

            String content = k.getContent() == null ? "" : k.getContent();
            sb.append(content.length() > MAX_CONTENT
                    ? content.substring(0, MAX_CONTENT) + "\n…（正文过长，已截断）"
                    : content);

            return ToolResult.text(sb.toString());

        } catch (Exception e) {
            log.warn("[工具] read_knowledge 执行失败：{}", e.getMessage());
            return ToolResult.text("读取失败：" + e.getMessage() + "。可以先搜索确认 id，再读。");
        }
    }
}
