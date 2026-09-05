package com.example.kb.service;

import com.example.kb.entity.Knowledge;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {
    //从application,properties读取配置
    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("{ai.deepseek.api-key}")
    private String apiKey;

    @Value("{ai.deepseek.model}")
    private String model;

    @Value("{ai.deepseek.timeout-seconds:30}")
    private int timeoutSeconds;

    //Jackson 解析器
    private static final ObjectMapper mapper =new ObjectMapper();

    // OkHttp 客户端（全局只建一个，每次调用复用）
    private OkHttpClient okHttpClient;

    //首次使用时创建懒加载 OkHttpClient
    private OkHttpClient getOkHttpClient(){
        if(okHttpClient == null)
        {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)//连接超时
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)//读取超时，TimeUnit==时间单位，SECONDS==秒
                    .build();
        }
        return okHttpClient;
    }

    public int scoreKnowledge(Knowledge knowledge){
        try{
            //构造Prompt(提示词)
            String prompt = buildPrompt(knowledge);

            //构造 Deepseek 请求体 JSON
            java.util.Map<String,Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model",model);

            java.util.List<java.util.Map<String,String>> messages = new java.util.ArrayList<>();

            java.util.Map<String,String> systemMsg = new java.util.HashMap<>();
            systemMsg.put("role","system");
            systemMsg.put("content","你是一个严格的学习内容价值评估专家。只输出JSON，不要输出如何其他文字，解释，markdown代码块标记。");
            messages.add(systemMsg);

            java.util.Map<String,String> userMsg = new java.util.HashMap<>();
            userMsg.put("role","user");
            userMsg.put("content",prompt);
            messages.add(userMsg);

            requestBody.put("messages",messages);
            requestBody.put("temperature",0.1);
            //deepseek专门提供的转JSON模式的开关，加了之后ai输出一定是合法JSON
            requestBody.put("response_format",java.util.Map.of("type","json_object"));

            //用Jackson把map序列化成JSON字符串
            String jsonBody = mapper.writeValueAsString(requestBody);

            //构造OkHttp请求
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization","Bearer" + apiKey)
                    .addHeader("Content-Type","application/json")
                    .post(body)
                    .build();

        //发送请求，读取响应
        try (Response response = getOkHttpClient().newCall(request).execute()) {

            // 先判断 HTTP 状态码，如果请求不成功就返回0并
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                System.err.println("[AI评分] DeepSeek 返回错误：HTTP " + response.code() + " " + errorBody);
                return 0;
            }

            // 解析 JSON 响应
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = mapper.readTree(respBody);

            // 做兼容解析
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                System.err.println("[AI评分] 响应缺少 choices 字段：" + respBody);
                return 0;
            }
            String content = choices.get(0).get("message").get("content").asText();

            // 解析 AI 返回的 JSON（形如 {"score":4, "reason":"..."}）
            // 有些 AI 可能会在 JSON 前后加 ```json ``` 之类的 markdown 标记，先清理
            content = cleanJson(content);
            JsonNode resultNode = mapper.readTree(content);
            int score = resultNode.has("score") ? resultNode.get("score").asInt() : 0;

            // 校验分数范围（1-5 才合法，否则 0）
            return (score >= 1 && score <= 5) ? score : 0;

        } catch (IOException e) {
            System.err.println("[AI评分] 请求失败：" + e.getMessage());
            return 0;
        }
    } catch (Exception e) {
        System.err.println("[AI评分] 整体异常：" + e.getMessage());
        return 0;
    }
}

/**
 * 构造 Prompt（把 Knowledge 的字段塞给 AI）
 */
private String buildPrompt(Knowledge knowledge) {
    return """
            请你作为一个"学习价值评估专家"，给下面这条"用户从AI对话中学到的内容"打分。
            
            打分范围 1-5 星，标准如下：
            ⭐ 1 星：完全无学习价值（纯闲聊、打招呼、简单操作确认，如"好了"、"下一步干嘛"）
            ⭐ 2 星：低价值（很基础的操作步骤，如"点击创建项目"、"打开终端"、"安装软件"，随时能查到）
            ⭐ 3 星：一般价值（具体代码写法/命令/报错原因，偶尔需要回顾）
            ⭐ 4 星：高价值（重要概念理解/设计模式/架构决策/踩坑经验/分层架构职责，经常需要回看）
            ⭐ 5 星：核心价值（根本性编程思维/原则/YAGNI/分层/关注点分离/DRY/自己总结的经验教训，必须反复琢磨）
            
            返回严格的 JSON 格式（不要加其他文字、不要加 markdown 代码块）：
            {"score": 3, "reason": "简短理由，15字以内"}
            
            现在评估以下内容：
            标题：%s
            学到的知识点：
            %s
            结果摘要：%s
            """.formatted(
            knowledge.getTitle(),
            knowledge.getContent() != null ? knowledge.getContent() : "（无知识点记录）",
            knowledge.getPersonalNote() != null ? knowledge.getPersonalNote() : "（无）"
    );
}

/**
 * 清理 AI 可能返回的 ```json ``` 包裹或前后空白
 */
private String cleanJson(String raw) {
    String s = raw.trim();
    // 去掉 ```json ... ``` 包裹
    if (s.startsWith("```")) {
        int firstNewline = s.indexOf('\n');
        int lastBacktick = s.lastIndexOf("```");
        if (firstNewline > 0 && lastBacktick > firstNewline) {
            s = s.substring(firstNewline + 1, lastBacktick).trim();
        }
    }
    return s;
}


}
