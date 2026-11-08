package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.review.ReviewCard;
import com.example.kb.review.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工具：列出已经复习过的卡片。
 *
 * 只读。跟 list_due_cards 的区别是它查"历史"而不是"待办"：
 * last_reviewed_at 非空的才算复习过，按最近复习时间倒序。
 */
@Slf4j
@Component
public class ListReviewedCardsTool implements AgentTool {

    private static final int LIMIT = 10;

    @Autowired
    private ReviewService reviewService;

    @Override
    public String name() {
        return "list_reviewed_cards";
    }

    @Override
    public String description() {
        return "列出用户已经复习过的卡片，按最近复习时间倒序。"
                + "当用户问'我之前复习过什么/复习了哪些卡/最近复习的几张'时使用。"
                + "不要用于：查今天到期的待复习卡（那是 list_due_cards 的职责）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public ToolResult execute(String argumentsJson, Long userId) {
        try {
            List<ReviewCard> cards = reviewService.reviewedCards(userId, LIMIT);
            if (cards.isEmpty()) {
                return ToolResult.text("还没有复习过任何卡片。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("复习过 ").append(cards.size()).append(" 张（最近复习的在前）：\n");
            for (int i = 0; i < cards.size(); i++) {
                ReviewCard c = cards.get(i);
                sb.append(i + 1).append(". ").append(c.getQuestion())
                        .append("（第 ").append(c.getStage()).append(" 阶");
                LocalDateTime last = c.getLastReviewedAt();
                if (last != null) {
                    String t = last.toString().replace("T", " ");
                    sb.append("，最近 ").append(t.length() > 16 ? t.substring(0, 16) : t);
                }
                sb.append("）\n");
            }
            sb.append("可以据此帮用户回顾，或建议再复述一遍。");

            return ToolResult.text(sb.toString());

        } catch (Exception e) {
            log.warn("[工具] list_reviewed_cards 执行失败：{}", e.getMessage());
            return ToolResult.text("查询复习记录失败：" + e.getMessage());
        }
    }
}
