package com.example.kb.agent;

import com.example.kb.service.AiService;
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
            2. 用户问今天该复习什么 → 调 list_due_cards
            3. 用户问以前学过/记过什么 → 调 search_knowledge
            4. 拿到工具结果后，用中文自然地告诉用户结论，不要把原始 JSON 复述给他
            5. 一次可以调多个工具，但不要重复调同一个；工具报错了就告诉用户，别硬试
            """;

    @Autowired
    private AiService aiService;

    @Autowired
    private AgentToolRegistry registry;

    public AgentReply chat(AgentChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

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
                        : tool.execute(args);

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
