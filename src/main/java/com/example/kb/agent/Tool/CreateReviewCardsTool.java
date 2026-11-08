package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.review.ReviewCard;
import com.example.kb.review.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具：为某条知识生成复习卡，加入间隔重复调度。
 *
 * 这是唯一一个"改了用户复习计划"的工具——用户说"把这个加进复习"时模型调它。
 * createCards 是幂等的：已经建过就直接返回已有的卡，不会重复生成。
 */
@Slf4j
@Component
public class CreateReviewCardsTool implements AgentTool {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ReviewService reviewService;

    @Override
    public String name() {
        return "create_review_cards";
    }

    @Override
    public String description() {
        return "为某一条知识生成复习卡，加入间隔重复计划。"
                + "当用户说'给它出张卡/把这个加进复习/我想复习这条'时使用。"
                + "重复调用是安全的：已经建过会返回已有的卡，不会重复生成。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "knowledgeId", Map.of(
                                "type", "integer",
                                "description", "知识 id，从 search_knowledge 的 [id=数字] 或刚入库的草稿 id 取"
                        )
                ),
                "required", List.of("knowledgeId")
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, Long userId) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            long knowledgeId = args.path("knowledgeId").asLong(0);
            if (knowledgeId <= 0) {
                return ToolResult.text("没有拿到有效的知识 id。");
            }

            List<ReviewCard> cards = reviewService.createCards(knowledgeId, userId);
            if (cards.isEmpty()) {
                return ToolResult.text("这条知识没能生成复习卡，可能内容太少。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("已为这条知识准备好 ").append(cards.size()).append(" 张复习卡。\n");
            for (int i = 0; i < cards.size(); i++) {
                ReviewCard c = cards.get(i);
                sb.append(i + 1).append(". ").append(c.getQuestion())
                        .append("（第 ").append(c.getStage()).append(" 阶）\n");
            }
            sb.append("从今天起会出现在「今日复习」里。");

            return ToolResult.text(sb.toString());

        } catch (Exception e) {
            log.warn("[工具] create_review_cards 执行失败：{}", e.getMessage());
            return ToolResult.text("生成复习卡失败：" + e.getMessage() + "。可以先确认这条知识还在。");
        }
    }
}
