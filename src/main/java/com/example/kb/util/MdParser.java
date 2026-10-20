package com.example.kb.util;

import com.example.kb.entity.Knowledge;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Trae IDE 导出的完整对话 Markdown 文件。
 * 格式约定：**User** 与 **Assistant** 各自单独占一行，
 * 一组「User 提问 + Assistant 回答」切分为一条候选知识点。
 */
public class MdParser {

    /** AI 回答少于这个字符数视为寒暄/简短指令，不值得入库（省一次 AI 评分调用） */
    private static final int MIN_CONTENT_LENGTH = 80;

    /** 个人笔记（用户提问原文）最大保留长度，防止超长提问撑爆字段 */
    private static final int MAX_NOTE_LENGTH = 500;

    /** 知识点内容最大保留长度，保护 AI 评分时的 Prompt 尺寸 */
    private static final int MAX_CONTENT_LENGTH = 8000;

    /** 标题最大长度 */
    private static final int MAX_TITLE_LENGTH = 40;

    // 状态机的三个状态
    private static final String STATE_NONE = "NONE";
    private static final String STATE_USER = "USER";
    private static final String STATE_ASSISTANT = "ASSISTANT";

    public static List<Knowledge> parse(InputStream inputStream) throws Exception {
        List<Knowledge> list = new ArrayList<>();

        String state = STATE_NONE;
        String userText = null;          // 当前这组对话的用户提问（遇到 Assistant 时定稿）
        StringBuilder buffer = new StringBuilder(); // 当前状态的攒文本桶

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)))  {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.equals("**User**")) {
                    // 遇到新提问：先把上一组「问答对」冲刷成知识点
                    if (state == STATE_ASSISTANT && userText != null) {
                        Knowledge k = buildKnowledge(userText, buffer.toString());
                        if (k != null) {
                            list.add(k);
                        }
                    }
                    // 开启新一轮，重置收集状态
                    state = STATE_USER;
                    userText = null;
                    buffer = new StringBuilder();
                    continue;
                }

                if (trimmed.equals("**Assistant**")) {
                    // 用户发言定稿
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

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(extractTitle(userText));
        knowledge.setContent(content);

        // 个人笔记存用户的完整提问：记录"当初为什么问这个问题"
        String note = userText;
        if (note.length() > MAX_NOTE_LENGTH) {
            note = note.substring(0, MAX_NOTE_LENGTH);
        }
        knowledge.setPersonalNote(note);

        knowledge.setSource("TRAE_EXPORT");
        knowledge.setCategory("编程");
        knowledge.setTags("");
        knowledge.setStarRating(0);
        knowledge.setAiScore(0);
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledge.setUpdatedAt(LocalDateTime.now());
        return knowledge;
    }

    /** 取用户提问的第一行有效文字作为标题（剥掉 Markdown 符号，截断到安全长度） */
    private static String extractTitle(String userText) {
        for (String line : userText.split("\n")) {
            String t = line.replaceAll("[*`#>\\[\\]]", "").trim();
            if (!t.isEmpty()) {
                return t.length() > MAX_TITLE_LENGTH ? t.substring(0, MAX_TITLE_LENGTH) : t;
            }
        }
        return "未命名对话";
    }
}
