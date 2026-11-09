package com.example.kb.agent;

import com.example.kb.service.AiService;
import com.example.kb.service.AuthService;
import com.example.kb.service.dto.AgentChatRequest;
import com.example.kb.service.dto.AgentMessage;
import com.example.kb.service.dto.AgentReply;
import com.example.kb.service.dto.ExtractResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 主体：一个"模型决定调什么工具"的循环。
 *
 * 一次用户消息可能触发 0~N 次工具调用：
 * 模型读工具清单 → 返回 tool_calls → 我们执行 → 结果回喂 → 模型再决定 → 直到它给出最终回复。
 *
 * 这是整个项目唯一一处"编排由模型做"的地方，其余都是确定性代码。
 */
@Slf4j
@Service
public class AgentService {

    /** 最多允许模型连续调 5 轮工具，防止它反复调同一个把 token 烧光 */
    private static final int MAX_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            你是个人知识库的 Agent。你可以调用工具来完成任务，不要凭空编造知识库里的数据。
            规则：
            1. 用户给出长文本并想整理 → 调 extract_knowledge
            2. 用户问今天该复习什么、有几张卡到期 → 调 list_due_cards
            3. 用户问之前复习过什么、复习了哪些卡 → 调 list_reviewed_cards
            4. 用户问以前学过/记过什么内容 → 调 search_knowledge
            5. 用户想展开看某一条、要完整正文 → 调 read_knowledge，id 从搜索结果的 [id=数字] 里取
            6. 用户说要"给这条出张卡/加进复习/我想复习它" → 调 create_review_cards
            7. 用户要求精简、改写、纠正或更新已有知识卡 → 先用 search_knowledge/read_knowledge 确认 id，再调 edit_knowledge；
               编辑指令要忠实表达用户要求，不得只凭标题猜 id，也不得把编辑做成新建一条
            8. 拿到工具结果后，用中文自然地告诉用户结论，不要把原始 JSON 复述给他
            9. 一次可以调多个工具，但不要重复调同一个；工具报错了就告诉用户，别硬试
            10. 回复用纯文本：不要用 ** 加粗、# 标题、` 代码围栏这些 Markdown 标记；
               需要分点时用「1. 2. 3.」的中文序号，需要强调时直接写清楚，不用符号
            """;

    @Autowired
    private AiService aiService;

    @Autowired
    private AgentToolRegistry registry;

    @Autowired
    private AuthService authService;

    public AgentReply chat(AgentChatRequest request, Long userId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // 风格指令拼在最前面：模型对开头和中段的注意力最强，放末尾容易被忽略
        messages.add(Map.of("role", "system",
                "content", styleInstruction(userId, request.customRule()) + SYSTEM_PROMPT));

        if (request.history() != null) {
            for (AgentMessage m : request.history()) {
                if (m.role() == null || m.content() == null || m.content().isBlank()) {
                    continue;   // Map.of 不接受 null 值，脏数据直接跳过
                }
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", request.message()));

        List<ExtractResult.Item> cards = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        List<Map<String, Object>> toolsJson = buildToolsJson();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            JsonNode msg = aiService.chatWithTools(messages, toolsJson);
            if (msg == null) {
                return new AgentReply("模型暂时不可用，请稍后再试。", cards, steps);
            }

            JsonNode toolCalls = msg.get("tool_calls");
            if (toolCalls == null || !toolCalls.isArray() || toolCalls.size() == 0) {
                // 没有工具调用 = 模型认为可以收尾了
                return new AgentReply(msg.path("content").asText(""), cards, steps);
            }

            // 关键：assistant 这条带 tool_calls 的消息必须原样回填进 history，
            // 否则下一轮请求会因为"tool 消息找不到对应的 tool_call_id"而 400
            messages.add(toAssistantMessage(msg));

            for (JsonNode call : toolCalls) {
                String name = call.path("function").path("name").asText("");
                String args = call.path("function").path("arguments").asText("");
                String callId = call.path("id").asText("");

                AgentTool tool = registry.find(name);
                ToolResult result = tool == null
                        ? ToolResult.text("没有叫 " + name + " 的工具。")
                        : tool.execute(args, userId);

                steps.add("调用 " + name);
                cards.addAll(result.cards());

                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", callId,
                        "content", result.text()
                ));
            }
        }

        log.warn("[Agent] 超过 {} 轮仍未收尾，强制返回", MAX_ROUNDS);
        return new AgentReply("这个问题需要好几步，我先停在这里，你可以拆开一次只做一件事。", cards, steps);
    }

    /**
     * 按用户设置的回答风格追加一段指令。
     * 规则存在 user 表里（EXPLAIN / EXAM / SOCRATIC / CUSTOM），改一次对所有对话生效。
     * CUSTOM 的具体文案不存库，由前端随请求带过来，空着就退回讲解模式。
     */
    private String styleInstruction(Long userId, String customRule) {
        String rule;
        try {
            rule = authService.findById(userId).agentRule();
        } catch (Exception e) {
            log.warn("[Agent] 读取回答风格失败，退回默认：{}", e.getMessage());
            return "";
        }
        if (rule == null) {
            rule = "EXPLAIN";
        }

        return switch (rule) {
            case "EXAM" -> "【必须遵守】考试模式：这一整轮回复里，禁止直接给出答案、结论或解释。"
                    + "你只能抛出一个问题让用户先回答，问句必须以问号结尾。\n";
            case "SOCRATIC" -> "【必须遵守】苏格拉底模式：用连续反问引导用户自己推理，"
                    + "禁止直接给现成结论，每轮回复至少包含一个问号。\n";
            case "CUSTOM" -> customInstruction(customRule);
            default -> "回复风格：讲解模式。像老师一样把原理讲清楚，必要时举例。\n";
        };
    }

    /**
     * 自定义风格：用户自己的原话直接进 system prompt。
     * 限制 200 字：再长会挤掉后面的工具规则，模型反而不会调工具了。
     * 用户写的是自己的偏好，不需要防范注入——他只能影响自己的对话。
     */
    private String customInstruction(String customRule) {
        String text = customRule == null ? "" : customRule.trim();
        if (text.isBlank()) {
            return "回复风格：讲解模式。像老师一样把原理讲清楚，必要时举例。\n";
        }
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        return "【必须遵守】这一轮按用户设定的风格回复：" + text + "\n";
    }

    /** 把各个工具的说明书拼成 OpenAI 的 tools 数组 */
    private List<Map<String, Object>> buildToolsJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AgentTool tool : registry.all()) {
            list.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.parameters()
                    )
            ));
        }
        return list;
    }

    /** 模型返回的 message 节点转成可直接塞回 messages 的 Map */
    private Map<String, Object> toAssistantMessage(JsonNode msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", "assistant");
        m.put("content", msg.path("content").asText(""));
        m.put("tool_calls", msg.get("tool_calls"));
        return m;
    }
}
