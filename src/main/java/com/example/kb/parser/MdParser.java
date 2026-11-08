package com.example.kb.parser;

import com.example.kb.entity.Knowledge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 解析器，支持两种文件：
 *
 * 1) Trae IDE 导出的对话 md：**User** / **Assistant** 各自单独占一行，
 *    一组「提问 + 回答」= 一条候选知识（原有行为，保持不变）。
 * 2) 普通笔记 md：没有上面两个标记时走兜底——按 #/## 标题分节，
 *    没有标题就按段落攒块，每块 = 一条候选知识。
 *
 * 第 2 种是必须有的：评委或用户随手丢一份笔记进来，如果解析出 0 条，
 * 接口只会回一句"文件里没有解析出内容"，整条演示就断在这里。
 */
public class MdParser {

    /** 正文少于这个长度视为标题/目录/寒暄，不值得入库（省一次 AI 评分调用） */
    private static final int MIN_CONTENT_LENGTH = 80;

    /**
     * 普通笔记按"节"切，一节本身就是完整的知识点，门槛要低得多。
     * 用 80 会把"Spring 循环依赖是两个 Bean 互相注入……"这种 70 字的好段落直接扔掉。
     */
    private static final int MIN_SECTION_LENGTH = 30;

    /** 个人笔记（用户提问原文）最大保留长度，防止超长提问撑爆字段 */
    private static final int MAX_NOTE_LENGTH = 500;

    /** 知识点内容最大保留长度，保护 AI 评分时的 Prompt 尺寸 */
    private static final int MAX_CONTENT_LENGTH = 8000;

    /** 标题最大长度 */
    private static final int MAX_TITLE_LENGTH = 40;

    /** 普通笔记里，一段攒到这么长就在段落边界切一条 */
    private static final int GENERIC_CHUNK_LENGTH = 1200;

    /** 一到六级标题 */
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*");

    // Trae 对话解析的状态机
    private static final String STATE_NONE = "NONE";
    private static final String STATE_USER = "USER";
    private static final String STATE_ASSISTANT = "ASSISTANT";

    public static List<Knowledge> parse(InputStream inputStream) throws IOException {
        // 先整体读进内存：判断格式和兜底分块都要回头再看一遍，流只能读一次
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("**User**") || trimmed.equals("**Assistant**")) {
                return parseTrae(lines);
            }
        }
        return parsePlainNote(lines);
    }

    // ==================== 格式一：Trae 对话导出 ====================

    private static List<Knowledge> parseTrae(List<String> lines) {
        List<Knowledge> list = new ArrayList<>();

        String state = STATE_NONE;
        String userText = null;                     // 当前这组对话的提问（遇到 Assistant 时定稿）
        StringBuilder buffer = new StringBuilder();  // 当前状态攒文本的桶

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("**User**")) {
                // 遇到新提问：先把上一组「问答对」冲刷成知识点
                if (state == STATE_ASSISTANT && userText != null) {
                    Knowledge k = buildKnowledge(userText, buffer.toString());
                    if (k != null) {
                        list.add(k);
                    }
                }
                state = STATE_USER;
                userText = null;
                buffer = new StringBuilder();
                continue;
            }

            if (trimmed.equals("**Assistant**")) {
                if (state == STATE_USER) {
                    userText = buffer.toString().trim();
                }
                state = STATE_ASSISTANT;
                buffer = new StringBuilder();
                continue;
            }

            // 跳过文件头、工作区标注和分隔线
            if (trimmed.equals("---")
                    || trimmed.startsWith("# ")
                    || trimmed.startsWith("> **Workspace:**")) {
                continue;
            }

            buffer.append(line).append("\n");
        }

        // 文件结束：冲刷最后一组问答对
        if (state == STATE_ASSISTANT && userText != null) {
            Knowledge k = buildKnowledge(userText, buffer.toString());
            if (k != null) {
                list.add(k);
            }
        }
        return list;
    }

    /**
     * 把一组「提问 + 回答」组装成 Knowledge 实体。
     * 返回 null 表示这一组不值得入库（回答太短）。
     */
    private static Knowledge buildKnowledge(String userText, String assistantText) {
        String content = assistantText.trim();
        if (content.length() < MIN_CONTENT_LENGTH) {
            return null;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        Knowledge knowledge = build(content, extractTitle(userText), "TRAE_EXPORT");
        knowledge.setCategory("编程");

        // 个人笔记存用户的完整提问：记录"当初为什么问这个问题"
        String note = userText;
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            note = note.substring(0, MAX_NOTE_LENGTH);
        }
        knowledge.setPersonalNote(note);
        return knowledge;
    }

    // ==================== 格式二：普通笔记 md ====================

    private static List<Knowledge> parsePlainNote(List<String> lines) {
        List<Knowledge> list = new ArrayList<>();

        StringBuilder buffer = new StringBuilder();
        String sectionTitle = null;      // 当前 # 标题，只给这一节的第一条用
        boolean firstChunkOfSection = true;
        boolean inFence = false;         // 代码块里的 # 不是标题
        boolean inFrontMatter = false;   // 文件头 --- 之间的 YAML 元信息
        boolean firstLine = true;

        for (String line : lines) {
            String trimmed = line.trim();

            if (firstLine && trimmed.equals("---")) {
                inFrontMatter = true;
                firstLine = false;
                continue;
            }
            firstLine = false;

            if (inFrontMatter) {
                if (trimmed.equals("---")) {
                    inFrontMatter = false;
                }
                continue;
            }

            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                buffer.append(line).append('\n');
                continue;
            }

            if (!inFence && HEADING.matcher(trimmed).matches()) {
                // 新标题：先把上一节冲刷掉，再把标题本身作为下一节的名字
                flushPlain(list, sectionTitle, buffer, firstChunkOfSection);
                buffer.setLength(0);
                sectionTitle = cleanTitle(trimmed);
                firstChunkOfSection = true;
                continue;
            }

            buffer.append(line).append('\n');

            // 到空行且攒够了 → 在这里切，保证是按段落边界切的
            if (trimmed.isEmpty() && buffer.length() >= GENERIC_CHUNK_LENGTH) {
                flushPlain(list, sectionTitle, buffer, firstChunkOfSection);
                buffer.setLength(0);
                firstChunkOfSection = false;
            } else if (buffer.length() >= MAX_CONTENT_LENGTH) {
                // 通篇没有空行的极端情况：别等到超长才切
                flushPlain(list, sectionTitle, buffer, firstChunkOfSection);
                buffer.setLength(0);
                firstChunkOfSection = false;
            }
        }

        flushPlain(list, sectionTitle, buffer, firstChunkOfSection);
        return list;
    }

    private static void flushPlain(List<Knowledge> list, String sectionTitle,
                                   StringBuilder buffer, boolean firstChunk) {
        String content = buffer.toString().trim();
        if (content.length() < MIN_SECTION_LENGTH) {
            return;   // 太短：标题、目录、一句话提醒，都不入库
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        String title = firstChunk && sectionTitle != null && !sectionTitle.isBlank()
                ? sectionTitle
                : extractTitle(content);

        list.add(build(content, title, "MD_IMPORT"));
    }

    // ==================== 公共 ====================

    private static Knowledge build(String content, String title, String source) {
        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setSource(source);
        knowledge.setCategory("");
        knowledge.setTags("");
        knowledge.setStarRating(0);
        knowledge.setAiScore(0);

        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        return knowledge;
    }

    /** 取一段文本的第一行有效文字作为标题（剥掉 Markdown 符号，截断到安全长度） */
    private static String extractTitle(String text) {
        if (text == null) {
            return "未命名笔记";
        }
        for (String line : text.split("\n")) {
            String t = line.replaceAll("[*`#>\\[\\]]", "").trim();
            if (!t.isEmpty()) {
                return t.length() > MAX_TITLE_LENGTH ? t.substring(0, MAX_TITLE_LENGTH) : t;
            }
        }
        return "未命名笔记";
    }

    /** 标题行：去掉开头的 # 号和其他 Markdown 装饰 */
    private static String cleanTitle(String headingLine) {
        String t = headingLine.replaceAll("^#{1,6}\\s*", "")
                .replaceAll("[*`#>\\[\\]]", "")
                .trim();
        if (t.isEmpty()) {
            return "未命名笔记";
        }
        return t.length() > MAX_TITLE_LENGTH ? t.substring(0, MAX_TITLE_LENGTH) : t;
    }
}
