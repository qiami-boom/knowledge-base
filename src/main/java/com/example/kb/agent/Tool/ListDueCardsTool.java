package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.review.ReviewCard;
import com.example.kb.review.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具：列出今天到期的复习卡。
 * 只读工具：无参数、无副作用、不产生草稿卡片，只返回一段文本。
 * 模型拿到"4 张到期，第一张问的是…"之后，自己会组织回复和下一步建议。
 */
@Slf4j
@Component
public class ListDueCardsTool implements AgentTool {

    @Autowired
    private ReviewService reviewService;

    @Override
    public String name(){
        return "list_due_cards";
    }

    @Override
    public String description(){
        return "列出今天到期的卡，需要复习的卡片"
                + "当用户问’今天要复习什么/还有几张卡到期/接下来该学什么‘以及类似语义的话时使用。"
                + "不要在用户提供新内容，要整理或查询知识库内容时使用。";
    }
    @Override
    public Map<String, Object> parameters(){
        return Map.of("type", "object", "properties", Map.of());
    }

    /**
     * @param argumentsJson 模型生成的参数 JSON 字符串（注意是字符串，不是对象）
     * @return text 回喂给模型继续推理；cards 给前端渲染，可为空
     * 失败时返回文本错误，不能抛异常——抛出去会中断整个 Agent 循环
     */
    @Override
    public ToolResult execute(String argumentsJson, Long userId){
        try{
            List<ReviewCard> cards = reviewService.dueCards(userId);
            if(cards.isEmpty()){
                return ToolResult.text("今天没有到期的复习卡。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("今天有 ").append(cards.size()).append(" 张卡到期: \n");
            for(int i = 0; i < cards.size(); i++){
                ReviewCard c = cards.get(i);
                sb.append(i + 1).append(". ").append(c.getQuestion())
                        .append("(第 ").append(c.getStage()).append(" 阶)\n");
            }
            sb.append("可以问用户想从哪张开始。");

            return ToolResult.text(sb.toString());

        } catch (Exception e){
            log.warn("[工具] list_due_cards 执行失败：{}",e.getMessage());
            return ToolResult.text("查询复习卡失败：" + e.getMessage());
        }
    }

}
