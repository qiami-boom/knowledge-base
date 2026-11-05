package com.example.kb.service;

import com.example.kb.entity.Knowledge;
import com.example.kb.review.ChoiceQuestion;
import com.example.kb.review.QuizQuestion;
import com.example.kb.service.dto.ExtractItem;
import com.example.kb.service.dto.RawScore;
import com.example.kb.service.dto.ScoreResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AiService {
    //从application,properties读取配置
    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.model}")
    private String model;

    @Value("${ai.deepseek.timeout-seconds:30}")
    private int timeoutSeconds;

    //Jackson 解析器，`static final` 表示"这个类的所有实例共享同一个 mapper，且不可被重新赋值"
    private static final ObjectMapper mapper = new ObjectMapper();

    // OkHttp 客户端（全局只建一个，每次调用复用）
    private OkHttpClient okHttpClient;

    //首次使用时创建懒加载 OkHttpClient
    private OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)//连接超时
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)//读取超时，TimeUnit==时间单位，SECONDS==秒
                    .build();
        }
        return okHttpClient;
    }

    /**
     * 业务判定
     * @param knowledge
     * @return
     */
    public ScoreResult scoreKnowledge(Knowledge knowledge) {
        String prompt = buildPrompt(knowledge);

        //`callAI(prompt)` 返回的是 AI 的 原始文本 ，可能是空串（请求失败）——所以先判空，返回`UNKNOWN/0`
        String json = callAI(prompt);
        if (json == null || json.isBlank()) {
            return new ScoreResult("UNKNOWN", 0, "", "AI调用失败");
        }

        //`cleanJson` 剥掉可能存在的包裹
        json = cleanJson(json);
        RawScore raw = parseJson(json, RawScore.class, "[AI评分]", null);
        if (raw == null) {
            return new ScoreResult("UNKNOWN", 0, "", "AI返回格式不正确");
        }

        // 模型漏给是非题，按格式错误处理；否则会静默判成 2 星
        if(raw.why() == null || raw.general() == null){
            return new ScoreResult("UNKNOWN", 0, "", "AI漏答是非题");
        }

        //`normalizeCategory` 做两件事：容忍大小写和空格；不认识的分类一律`UNKNOWN`
        String category = normalizeCategory(raw.category());
        if (!"KNOWLEDGE".equals(category)) {
            return new ScoreResult(category, 1, "", raw.reason(), raw.why(), raw.general());
        }

        // 两个是非题映射成分数，不再让模型自己填分
        int score = ScoreResult.scoreOf(raw.why(),raw.general());

        return new ScoreResult(category, score, raw.title(), raw.reason(), raw.why(), raw.general());
    }

    /**
     * 给模型返回的分类消毒，因为模型可能会给出错误形式的分类如”大小写错了，带了空格，翻译成了中文，或是没给“
     * @param raw
     * @return
     */
    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String c = raw.trim().toUpperCase();  //.trim()去空格 toUpperCase()统一大写
        return switch (c) {   //白名单只放行这四个值
            case "KNOWLEDGE", "LOGISTICS", "CHITCHAT", "OPERATION" -> c;
            default -> "UNKNOWN";   //别的都当失败
        };
    }


/**
 * 构造 Prompt（把 Knowledge 的字段塞给 AI）
 */
private String buildPrompt(Knowledge knowledge) {
    return """
            你是知识库的入库审核员。给你一段 AI 对话（用户提问 + AI 回答）。

            判断标准只有一条：三个月后，这个正在建设自己项目的用户回看，还有没有用。
            不要用"是否通用"当标准——本项目踩的坑、具体的报错根因、具体的写法，都算有用。

            第一步：归类，只能选一个
            KNOWLEDGE — 编程概念、机制解释、报错根因、设计取舍、踩坑、API 用法
            LOGISTICS — 赛事、报名、队名、答辩、材料格式这类一次性事务
            CHITCHAT  — 寒暄、确认、催促、要求重发或换格式
            OPERATION — 纯点击步骤，且没有解释"为什么"

            第二步：回答两个是非题，不要自己给分数
            why：这段内容解释了"为什么"吗？（机制、根因、设计取舍、原理）
            general：它换到另一个项目、另一门语言也成立吗？

            两个答案怎么变成分数，你不用管，后端会算：
            why=true,  general=true  → 5
            why=true,  general=false → 4
            why=false, general=true  → 3   （只给了做法：命令、配置、API 写法）
            why=false, general=false → 2   （只对这一次操作有效）

            只有 KNOWLEDGE 才答这两个问题；其他三类一律 1 分，两个都答 false。
            判断对象是 AI 的回答；回答里解释了"为什么"，就算用户只说了"写完了"也归 KNOWLEDGE。

            第三步：KNOWLEDGE 起标题，12-24 字，不要"我""你""下一步"这类对话痕迹；
            非 KNOWLEDGE 时 title 给空字符串。

            输出严格 JSON：
            {"category":"KNOWLEDGE","why":true,"general":false,"title":"文件名与类名不一致导致编译报错","reason":"根因｜公共类须与文件名同名"}
            reason 固定写成"证据类型｜一句话"，证据类型只能从这六个里挑：
            因果链、根因、边界、推导、做法、非知识。

            示例：
            1) 用户问队名怎么起，AI 给了起名标准 →
            {"category":"LOGISTICS","why":false,"general":false,"title":"","reason":"非知识｜赛事事务"}

            2) 用户问 @Autowired 怎么写，AI 只给了一行用法 →
            {"category":"KNOWLEDGE","why":false,"general":true,"title":"@Autowired 基本写法","reason":"做法｜仅给写法"}

            3) 用户问 RecallQuality 为什么报错，AI 指出文件名与类名不一致并讲了 Java 命名规则 →
            {"category":"KNOWLEDGE","why":true,"general":false,"title":"文件名与类名不一致导致编译报错","reason":"根因｜公共类须与文件名同名"}

            4) 用户问包该按层分还是按功能分，AI 讲了取舍与判断标准 →
            {"category":"KNOWLEDGE","why":true,"general":true,"title":"layer-first 与 feature-first 分包取舍","reason":"推导｜按变更频率选分包方式"}
            现在评估：
            用户提问：%s
            AI 回答：%s
            """.formatted(
            knowledge.getPersonalNote() != null && !knowledge.getPersonalNote().isBlank()
                    ? knowledge.getPersonalNote()
                    : knowledge.getTitle(),
            knowledge.getContent() != null ? knowledge.getContent() : "（无知识点记录）"
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

    /**
     * 让 AI 生成一篇笔记的候选标签
     * @param content
     * @param maxTags
     * @return 标签名列表，最多 maxTags 个
     */
    public List<String> generateTags(String content, int maxTags) {
        String prompt = """
                你是标签推荐专家。给下面的笔记内容生成 %d 个最贴切的中文或英文标签。
                要求：
                1. 每个标签是稳定的概念名，1~4 个字/词，不要包含标点和空格
                2. 不要用 "笔记"、"教程"、"学习" 这类泛化词
                3. 只返回严格 JSON，不要任何其他文字：
                {"tags": ["Spring Boot", "依赖注入"]}

                笔记内容：
                %s
                """.formatted(maxTags, content);
        String json = callAI(prompt);
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        json = cleanJson(json);
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode tags = root.get("tags");
            if (tags == null || !tags.isArray()) {
                return java.util.List.of();
            }
            java.util.List<String> result = new java.util.ArrayList<>();
            for (JsonNode node : tags) {
                String t = node.asText().trim();
                if (!t.isEmpty()) {
                    result.add(t);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[AI标签] 解析失败：" + e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * 从一批标题里挑出最像"可复用原理"的 topN 条
     * @return 被挑中的序号（从 1 开始）；调用失败返回空列表，调用方不要据此降级
     */
    public List<Integer> pickTopPrinciples(List<String> titles, int topN) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < titles.size(); i++) {
            list.append(i + 1).append(". ").append(titles.get(i)).append("\n");
        }
        String prompt = """
            下面是同一批入库的 %d 条知识标题。用户复习时间有限，每次只优先看最像"可复用原理"的 %d 条。
            挑选标准：讲的是设计原则、思维模型、架构取舍，而不是某个工具、框架、语言的用法或语法。

            只输出 JSON，不要任何其他文字：{"picks":[3,12,19,22,29,36,41,45]}
            编号是上面列表的序号，从 1 开始，不能超出范围，不能重复。

            %s
            """.formatted(titles.size(), topN, list);

        String json = callAI(prompt);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        json = cleanJson(json);
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode picks = root.get("picks");
            if (picks == null || !picks.isArray()) {
                return List.of();
            }
            List<Integer> result = new java.util.ArrayList<>();
            for (JsonNode node : picks) {
                int v = node.asInt();
                //越界和去重过滤,模型的编号输出经常出错
                if (v >= 1 && v <= titles.size() && !result.contains(v)) {
                    result.add(v);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[AI排序] 解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 负责Prompt->字符串
     * 公共调用：把 prompt 发给 DeepSeek，返回 AI 回复的正文内容
     * 从 scoreKnowledge 里抽出来的通用 HTTP 逻辑，generateTags / pickMostRelevantTag 复用
     *
     * @param prompt // 提示词
     * @return String
     */
    private String callAI(String prompt) {
        try {
            java.util.Map<String,Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", model);

            java.util.List<java.util.Map<String,String>> messages = new java.util.ArrayList<>();
            java.util.Map<String,String> systemMsg = new java.util.HashMap<>();
            systemMsg.put("role","system");
            systemMsg.put("content","你是严谨的专家助手。只输出JSON，不要输出其他文字、解释、markdown代码块标记。");
            messages.add(systemMsg);

            java.util.Map<String,String> userMsg = new java.util.HashMap<>();
            userMsg.put("role","user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("response_format", java.util.Map.of("type","json_object"));

            String jsonBody = mapper.writeValueAsString(requestBody);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization","Bearer " + apiKey)
                    .addHeader("Content-Type","application/json")
                    .post(body)
                    .build();

            try (Response response = getOkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.warn("[AI调用] DeepSeek 返回错误：HTTP " + response.code() + " " + errorBody);
                    return "";
                }
                String respBody = response.body() != null ? response.body().string() : "";
                JsonNode root = mapper.readTree(respBody);
                JsonNode choices = root.get("choices");//choices是候选回复的数组 。这个接口支持一次生成多条（参数n），默认只要一条，所以永远取第0个。
                if (choices == null || !choices.isArray() || choices.size() == 0) {
                    log.warn("[AI调用] 响应缺少 choices 字段：" + respBody);
                    return "";
                }
                return choices.get(0).get("message").get("content").asText();//取第0个deepseek返回信封的内容中的‘msg’‘cent’
            }
        } catch (Exception e) {
            log.warn("[AI调用] 请求失败：" + e.getMessage());
            return "";
        }
    }

    /**
     * AI 出题
     * @param knowledge // 知识
     * @return
     */
    public QuizQuestion generateQuizQuestion(Knowledge knowledge) {
        //构造prompt
        log.info("开始calldeepseek:{}",knowledge);
        String prompt = """
            你是记忆复习教练。根据下面的知识点，出一道能检验记忆的回忆型问题。
            要求：
            1. 只出一道问题，不提供选项
            2. 同时给出参考答案
            3. 只返回严格 JSON，不要任何其他文字：
            {"question": "问题", "answer": "参考答案"}

            知识点标题：%s
            知识点内容：%s
            个人备注：%s
            """.formatted(
                knowledge.getTitle(),
                knowledge.getContent(),
                knowledge.getPersonalNote());

        //拿到json
        String json = callAI(prompt);

        if(json == null || json.isBlank()){
            return new QuizQuestion("","");
        }

        json = cleanJson(json);
        
        try {
            return mapper.readValue(json, QuizQuestion.class);
        } catch (Exception e) {
            log.warn("[ai]输出失败：{}",json);
            return new QuizQuestion("","");
        }

    }

    /**
     * 复习出题（选择题版）：根据一条知识点生成一道四选一选择题
     * @param knowledge
     * @return
     */
    public ChoiceQuestion generateChoiceQuestion(Knowledge knowledge) {
        log.info("开始调取ai");
        // 1. 构造 Prompt
        String prompt = """
            你是记忆复习教练。根据下面的知识点，出一道四选一的选择题。
            要求：
            1. 四个选项，只有一个正确
            2. 正确选项的下标随机，不要固定（固定放第一个就没意义了）
            3. 给出解析
            4. 只返回严格 JSON，不要任何其他文字：
            {"stem": "题干", "options": ["选项A", "选项B", "选项C", "选项D"], "correctIndex": 2, "explanation": "解析"}

            知识点标题：%s
            知识点内容：%s
            个人备注：%s
            """.formatted(
                knowledge.getPersonalNote() != null && !knowledge.getPersonalNote().isBlank()
                        ? knowledge.getPersonalNote()
                        : knowledge.getTitle(),
                knowledge.getContent() != null ? knowledge.getContent() : "（无知识点记录）"
        );
        // 2. 发请求 + 判空
        String json = callAI(prompt);
        if (json == null || json.isBlank()) {
            return new ChoiceQuestion("", List.of(), -1, "");
        }

        // 3. 剥 ```json 包裹
        json = cleanJson(json);

        // 4. 解析：数组自动填进 List，不用手写循环
        return parseJson(json, ChoiceQuestion.class,
                "[AI出题-选择题]",
                new ChoiceQuestion("", List.of(), -1, ""));
    }

    /**
     * 判断选择题是否正确
     * @param choiceQuestion
     * @param getUserAnswer
     * @return
     */
    public Boolean yesOrNo(ChoiceQuestion choiceQuestion,int getUserAnswer) {
        if (choiceQuestion.correctIndex() != getUserAnswer){
            return false;
        }else{
            return true;
        }
    }

    



    /**
     * 字符串->对象
     * 统一解析：把 AI 返回的 JSON 转成目标类型；失败打日志 + 返回兜底值
     * @param json    AI 返回的 JSON 文字
     * @param type    要转成的类型（传 Xxx.class）
     * @param logTag  日志前缀，方便区分是哪个方法在解析
     * @param fallback 解析失败时返回的兜底值
     */
    private <T> T parseJson(String json, Class<T> type, String logTag, T fallback) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("{} 解析失败：{}", logTag, e.getMessage());
            return fallback;
        }
    }

    public List<ExtractItem> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String prompt = """
                你是知识抽取器。从下面这段对话里抽出 0~5 条值得长期保存的知识点。
                每条输出这五个字段：
                - title：24个字之内，说清讲了什么，不要照抄原话
                - content：知识点正文，保留对话里讲清楚的部分
                - why：这段内容解释了"为什么"吗？（机制、根因、设计取舍、原理）
                - general：它换到另一个项目、另一门语言也成立吗？
                - reason：证据类型｜一句话，证据类型只能从 因果链/根因/边界/推导/做法/非知识 里挑
                
                规则：
                1. 同一件事只抽一条，不要重复
                2. 寒暄、催问、日常生活问题、纯操作步骤都不抽
                3. 如果这段里没有值得保存的内容，返回 {"items":[]}，不要硬凑
                
                输出严格 JSON，不要任何其他文字：
                {"items":[{"title":"...","content":"...","why":true,"general":false,"reason":"根因｜..."}]}
                
                文本：
                %s
                """.formatted(text.length() > 15000 ? text.substring(0, 15000) : text);
        String json = callAI(prompt);  //将ai返回的自由文本转成json形式
        if (json == null || json.isBlank()) {
            return List.of();
        }
        json = cleanJson(json);
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                log.warn("[抽取] 返回结构不对，原始 JSON：{}", json);
                return List.of();
            }
            List<ExtractItem> result = new ArrayList<>();

            for (JsonNode item : items) {
                ExtractItem e = mapper.convertValue(item, ExtractItem.class);  //把一个`JsonNode` 直接转成指定类型的对象。
                                                                               // 它内部会把节点再序列化成 JSON 字符串，然后反序列化成`ExtractItem`
                if (e.title() == null || e.title().isBlank()) {
                    continue;
                }
                if(e.content() == null || e.content().isBlank()){
                    continue;
                }
                if(e.why() == null || e.general() == null){
                    continue;
                }
                result.add(e);
            }
            return result;

        }catch(Exception e){
            log.warn("[抽取] 解析失败：{}", e.getMessage());
            return List.of();
        }
    }


}