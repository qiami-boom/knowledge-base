package com.example.kb.service;

import com.example.kb.entity.Knowledge;
import com.example.kb.review.ChoiceQuestion;
import com.example.kb.review.QuizQuestion;
import com.example.kb.service.dto.ExtractItem;
import com.example.kb.service.dto.KnowledgeAiEditResult;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        List<String> tags = sanitizeTags(raw.tags());
        if (!"KNOWLEDGE".equals(category)) {
            return new ScoreResult(category, 1, "", raw.reason(), raw.why(), raw.general(),
                    raw.summary(), List.of());
        }

        // 两个是非题映射成分数，不再让模型自己填分
        int score = ScoreResult.scoreOf(raw.why(),raw.general());

        return new ScoreResult(category, score, raw.title(), raw.reason(), raw.why(), raw.general(),
                raw.summary(), tags);
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
            KNOWLEDGE — 任何学科的概念、机制、原理、推导、踩坑、具体用法（编程、英语、数学、物理都算）
            LOGISTICS — 赛事、报名、队名、答辩、材料格式这类一次性事务
            CHITCHAT  — 寒暄、确认、催促、要求重发或换格式
            OPERATION — 纯点击步骤，且没有解释"为什么"

            第二步：回答两个是非题，不要自己给分数
            why：这段内容解释了"为什么"吗？（机制、根因、设计取舍、原理）
            general：它换到另一个项目、另一门学科也成立吗？

            两个答案怎么变成分数，你不用管，后端会算：
            why=true,  general=true  → 5
            why=true,  general=false → 4
            why=false, general=true  → 3   （只给了做法：命令、配置、API 写法）
            why=false, general=false → 2   （只对这一次操作有效）

            只有 KNOWLEDGE 才答这两个问题；其他三类一律 1 分，两个都答 false。
            判断对象是 AI 的回答；回答里解释了"为什么"，就算用户只说了"写完了"也归 KNOWLEDGE。
            
            - summary：一句话概括这条知识讲了什么，30 字以内，用于检索时快速判断；不要重复 title，非 KNOWLEDGE 给空字符串
            - tags：KNOWLEDGE 给 1~3 个稳定的概念标签，不要泛化词；非 KNOWLEDGE 给空数组

            第三步：KNOWLEDGE 起标题，12-24 字，不要"我""你""下一步"这类对话痕迹；
            非 KNOWLEDGE 时 title 给空字符串。

            输出严格 JSON：
            {"category":"KNOWLEDGE","why":true,"general":false,"title":"文件名与类名不一致导致编译报错","summary":"Java 公共类须与文件名同名","tags":["Java","类名","编译错误"],"reason":"根因｜公共类须与文件名同名"}
            reason 固定写成"证据类型｜一句话"，证据类型只能从这六个里挑：
            因果链、根因、边界、推导、做法、非知识。

            示例：
            1) 用户问队名怎么起，AI 给了起名标准 →
            {"category":"LOGISTICS","why":false,"general":false,"title":"","summary":"","tags":[],"reason":"非知识｜赛事事务"}

            2) 用户问 @Autowired 怎么写，AI 只给了一行用法 →
            {"category":"KNOWLEDGE","why":false,"general":true,"title":"@Autowired 基本写法","summary":"按类型自动注入，不用手动 new 对象","tags":["Spring","依赖注入"],"reason":"做法｜仅给写法"}

            3) 用户问 RecallQuality 为什么报错，AI 指出文件名与类名不一致并讲了 Java 命名规则 →
            {"category":"KNOWLEDGE","why":true,"general":false,"title":"文件名与类名不一致导致编译报错","summary":"Java 公共类须与文件名同名","tags":["Java","类名","编译错误"],"reason":"根因｜公共类须与文件名同名"}

            4) 用户问包该按层分还是按功能分，AI 讲了取舍与判断标准 →
            {"category":"KNOWLEDGE","why":true,"general":true,"title":"layer-first 与 feature-first 分包取舍","summary":"按变更频率决定按层分还是按功能分","tags":["架构设计","分包","开闭原则"],"reason":"推导｜按变更频率选分包方式"}
            5) 用户问 affect 和 effect 怎么区分，AI 讲了词性和常见搭配 →
            {"category":"KNOWLEDGE","why":true,"general":true,"title":"affect 与 effect 的词性差异","summary":"affect 是动词，effect 是名词","tags":["英语","词性","易混词"],"reason":"边界｜按词性区分"}

            6) 用户问洛必达法则什么时候能用，AI 讲了 0/0 型的前提条件 →
            {"category":"KNOWLEDGE","why":true,"general":true,"title":"洛必达法则的适用前提","summary":"只适用于 0/0 与 ∞/∞ 型极限","tags":["高等数学","极限","洛必达法则"],"reason":"边界｜非此类型不可用"}

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
        int limit = Math.min(maxTags, 3);
        String prompt = """
                你是标签推荐专家。给下面的笔记内容生成 %d 个最贴切的中文或英文标签。
                要求：
                1. 每个标签是稳定的概念名，1~4 个字/词，不要包含标点和空格
                2. 不要用 "笔记"、"教程"、"学习" 这类泛化词
                3. 只返回严格 JSON，不要任何其他文字：
                {"tags": ["Spring Boot", "依赖注入"]}

                笔记内容：
                %s
                """.formatted(limit, content);
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
                    if (result.size() >= limit) break;
                }
            }
            return sanitizeTags(result);
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
                knowledge.getTitle() != null ? knowledge.getTitle() : "（无标题）",
                knowledge.getContent() != null ? knowledge.getContent() : "（无知识点记录）",
                knowledge.getPersonalNote() != null && !knowledge.getPersonalNote().isBlank()
                        ? knowledge.getPersonalNote()
                        : "（无备注）"
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
     * 根据用户指令编辑一张已有知识卡。
     * 只生成可替换内容，不在这里落库，也不允许 AI 改写个人备注和评分。
     */
    public KnowledgeAiEditResult editKnowledge(Knowledge knowledge, String instruction) {
        String prompt = """
                你是个人知识库的卡片编辑。请根据编辑指令重写已有知识卡。

                硬性规则：
                1. 只返回 title、content、summary、category、tags 五个字段
                2. 不得修改个人备注、用户评分、来源、状态、卡片 id 或用户归属
                3. 不得编造原卡片没有的事实；保留代码、命令、报错、公式和关键边界条件
                4. content 继续使用 Markdown，去掉重复表达和对话痕迹
                5. 如果指令是“精简”，优先压缩冗余，不要丢掉关键结论
                6. 只输出严格 JSON，不要输出解释或 Markdown 代码围栏

                返回格式：
                {"title":"24 字以内标题","content":"精简后的 Markdown 正文","summary":"一句话摘要","category":"分类","tags":"标签1,标签2"}

                编辑指令：
                %s

                当前标题：
                %s

                当前分类：
                %s

                当前标签：
                %s

                当前摘要：
                %s

                当前正文：
                %s
                """.formatted(
                instruction,
                valueOrEmpty(knowledge.getTitle()),
                valueOrEmpty(knowledge.getCategory()),
                valueOrEmpty(knowledge.getTags()),
                valueOrEmpty(knowledge.getSummary()),
                valueOrEmpty(knowledge.getContent())
        );

        String json = callAI(prompt);
        if (json == null || json.isBlank()) {
            return null;
        }

        return parseJson(cleanJson(json), KnowledgeAiEditResult.class,
                "[AI编辑知识卡]", null);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 标签最多保留 3 个，去掉空值并按不区分大小写去重。
     */
    private List<String> sanitizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : rawTags) {
            if (raw == null) continue;
            String tag = raw.trim();
            if (tag.isBlank()) continue;
            String key = tag.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            result.add(tag.length() > 40 ? tag.substring(0, 40) : tag);
            if (result.size() == 3) break;
        }
        return result;
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
                - content：知识点正文。把对话里讲清楚的那几段原文照搬过来，不要压缩成摘要,
                有代码、命令、公式、例题、报错原文就原样贴进来；原文本身很短就照抄，不要为了凑长度扩写
                - why：这段内容解释了"为什么"吗？（机制、根因、设计取舍、原理）
                - general：它换到另一个项目、另一门学科也成立吗？
                - reason：证据类型｜一句话，证据类型只能从 因果链/根因/边界/推导/做法/非知识 里挑
                - summary：一句话概括这条知识点，30 字以内，用于检索时快速判断
                - tags：1~3 个稳定的概念标签，不要泛化词
                
                规则：
                1. 同一件事只抽一条，不要重复
                2. 寒暄、催问、日常生活问题、纯操作步骤都不抽
                3. 如果这段里没有值得保存的内容，返回 {"items":[]}，不要硬凑
                
                输出严格 JSON，不要任何其他文字：
                {"items":[{"title":"...","content":"...","summary":"...","tags":["标签1","标签2"],"why":true,"general":false,"reason":"根因｜..."}]}
                
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
                result.add(new ExtractItem(
                        e.title(),
                        e.content(),
                        e.reason(),
                        e.why(),
                        e.general(),
                        e.summary(),
                        sanitizeTags(e.tags())
                ));
            }
            return result;

        }catch(Exception e){
            log.warn("[抽取] 解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    public record WebPage(String title, String content) {
    }

    /**
     * 调 DeepSeek Fetch 接口，把网页抓成 Markdown 正文
     * @return 抓不到返回 null，由调用方翻译成业务错误
     */
    public WebPage fetchUrl(String url) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("url", url);
            String jsonBody = mapper.writeValueAsString(body);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody rb = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/tools/fetch")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(rb)
                    .build();

            try (Response response = getOkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[抓取] 失败 HTTP {}，url={}", response.code(), url);
                    return null;
                }
                String respBody = response.body() == null ? "" : response.body().string();
                JsonNode root = mapper.readTree(respBody);
                return new WebPage(
                        root.path("title").asText(""),
                        root.path("content").asText("")
                );
            }
        } catch (Exception e) {
            log.warn("[抓取] 异常：{}，url={}", e.getMessage(), url);
            return null;
        }
    }

    /**
     * 带工具调用的对话请求。跟 callAI 的三个区别：
     * 1) 传完整 messages 数组，不是单条 prompt —— Agent 需要多轮上下文
     * 2) 带 tools，让模型自己决定调不调、调哪个
     * 3) 不设 response_format=json_object —— 它跟 tool_calls 冲突，模型会只吐 JSON 不调工具
     *
     * @param messages OpenAI 格式的消息数组（含 system / user / assistant / tool）
     * @param tools    工具清单，由 AgentToolRegistry 生成
     * @return choices[0].message 节点；失败返回 null，由调用方翻译成人话
     */
    public JsonNode chatWithTools(java.util.List<java.util.Map<String, Object>> messages,
                                  java.util.List<java.util.Map<String, Object>> tools) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("tool_choice", "auto");
            body.put("temperature", 0.1);

            String jsonBody = mapper.writeValueAsString(body);
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody rb = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(rb)
                    .build();

            try (Response response = getOkHttpClient().newCall(request).execute()) {
                String respBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    log.warn("[Agent] 请求失败 HTTP {}：{}", response.code(), respBody);
                    return null;
                }
                return mapper.readTree(respBody).path("choices").path(0).path("message");
            }
        } catch (Exception e) {
            log.warn("[Agent] 请求异常：{}", e.getMessage());
            return null;
        }
    }

}
