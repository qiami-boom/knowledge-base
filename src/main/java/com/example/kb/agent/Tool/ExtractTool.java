package com.example.kb.agent.Tool;

import com.example.kb.agent.AgentTool;
import com.example.kb.agent.ToolResult;
import com.example.kb.service.ExtractService;
import com.example.kb.service.dto.ExtractResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
/**
 * 工具：从一段文本中抽取知识点草稿。
 *
 * 这个类没有代码调用方——它由 Agent 循环按名字调用：
 * 模型读 description() 判断该不该用，用了之后 execute() 的结果
 * 会作为一条 tool 消息回喂给模型，模型再决定下一步。
 *
 * 副作用：会往 knowledge 表写入 status = DRAFT 的草稿，
 * 用户不确认就不会变成正式知识，丢弃即物理删除。
 */
@Slf4j
@Component
public class ExtractTool implements AgentTool {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ExtractService extractService;

    @Override
    public String name(){
        return "extract_knowledge";
    }

    @Override
    public String description(){
        return "从用户提供的一段文本里抽取 0~5 条值得长期保存的知识点，生成待用户确认的草稿。"
                + "当用户粘贴整段对话、笔记或文章，并表达'整理一下/提取知识点/看看有什么值得记的/存起来'时使用。"
                + "不要用于：查询已有知识、查看复习计划、闲聊、单纯改写文字。";
    }

    @Override
    public Map<String,Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "要抽取的原文，通常是用户粘贴的整段对话或文章"
                        )
                ),
                "required", List.of("text")
        );
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        try{
            JsonNode args = mapper.readTree(argumentsJson);
            String text = args.path("text").asText("");

            if(text.isBlank()){
                return ToolResult.text("没有收到要抽取的文本。");
            }

            ExtractResult result = extractService.extract(text);
            if(result.items().isEmpty()){
                return ToolResult.text("这段里没抽到值得保存的知识点。");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("抽出 ").append(result.items().size()).append(" 条草稿：\n");
            for (int i = 0; i < result.items().size(); i++) {
                ExtractResult.Item item = result.items().get(i);
                sb.append(i + 1).append(". ").append(item.title())
                        .append("（").append(item.aiScore()).append(" 星）\n");
            }
            sb.append("等用户确认哪几条入库。");

            return ToolResult.withCards(sb.toString(), result.items());

        } catch (Exception e) {
            // 工具内部报错绝不能往外抛：抛出去会中断整个 Agent 循环
            log.warn("[工具] extract_knowledge 执行失败：{}", e.getMessage());
            return ToolResult.text("抽取失败：" + e.getMessage() + "。可以换个说法或稍后再试。");
        }
    }

}
