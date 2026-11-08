package com.example.kb.parser;

import com.example.kb.entity.Knowledge;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Word（.docx）解析器。
 *
 * 按标题样式切节；没有标题样式时按长度切块。
 * 一份 20 页讲义必须产出多条知识点，而不是压成一条 15000 字的巨型笔记——
 * 那样只会生成 1 张复习卡，检索时也永远只能命中同一条。
 */
public class DocxParser {

    /** 单条知识正文上限 */
    public static final int MAX_CONTENT_LENGTH = 6000;

    /** 没有标题样式时，攒到这么长就在段落边界切一条 */
    private static final int CHUNK_LENGTH = 2000;

    /** 少于这个长度视为封面/页眉/一句话，不入库 */
    private static final int MIN_CONTENT_LENGTH = 60;

    private static final int MAX_TITLE_LENGTH = 40;

    public static List<Knowledge> parse(InputStream inputStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            List<Knowledge> list = new ArrayList<>();
            StringBuilder buffer = new StringBuilder();
            String sectionTitle = null;   // 当前标题样式对应的标题
            boolean firstChunk = true;

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String text = p.getText();
                    if (text == null || text.isBlank()) {
                        continue;   // 空段落直接跳过
                    }

                    if (isHeading(p)) {
                        // 新的一节：先把上一节冲刷掉
                        flush(list, sectionTitle, buffer, firstChunk);
                        buffer.setLength(0);
                        sectionTitle = cleanTitle(text);
                        firstChunk = true;
                        continue;
                    }
                    buffer.append(text.trim()).append('\n');

                } else if (element instanceof XWPFTable t) {
                    for (XWPFTableRow row : t.getRows()) {          // 第一层：行
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {   // 第二层：格子
                            cells.add(cell.getText().replace("\n", " ").trim());
                        }
                        buffer.append(String.join(" | ", cells)).append('\n');
                    }
                }

                if (buffer.length() >= CHUNK_LENGTH) {
                    flush(list, sectionTitle, buffer, firstChunk);
                    buffer.setLength(0);
                    firstChunk = false;
                }
            }

            flush(list, sectionTitle, buffer, firstChunk);
            return list;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("不是有效的 docx 文件", e);
        }
    }

    /**
     * 判断段落是不是标题。
     * Word 的标题样式名不统一：英文版是 Heading1，中文版常常直接是 "1"，
     * 所以 style 和 styleID 都要看，还不能只认 "Heading" 前缀。
     */
    private static boolean isHeading(XWPFParagraph p) {
        return looksLikeHeading(p.getStyle()) || looksLikeHeading(p.getStyleID());
    }

    private static boolean looksLikeHeading(String style) {
        if (style == null || style.isBlank()) {
            return false;
        }
        String s = style.trim().toLowerCase();
        return s.startsWith("heading")      // Heading 1 / Heading1
                || s.startsWith("标题")       // 中文样式名
                || s.matches("^\\d+$");     // 中文 Word 里 "标题 1" 的 styleID 常是 "1"
    }

    private static void flush(List<Knowledge> list, String sectionTitle,
                              StringBuilder buffer, boolean firstChunk) {
        String content = buffer.toString().trim();
        if (content.length() < MIN_CONTENT_LENGTH) {
            return;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        String title = firstChunk && sectionTitle != null && !sectionTitle.isBlank()
                ? sectionTitle
                : extractTitle(content);

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setSource("DOCX_IMPORT");
        knowledge.setCategory("");
        knowledge.setTags("");
        knowledge.setStarRating(0);
        knowledge.setAiScore(0);

        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        list.add(knowledge);
    }

    /** 取正文第一行有效文字当标题 */
    private static String extractTitle(String content) {
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > MAX_TITLE_LENGTH ? t.substring(0, MAX_TITLE_LENGTH) : t;
            }
        }
        return "文档片段";
    }

    private static String cleanTitle(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return "文档片段";
        }
        return t.length() > MAX_TITLE_LENGTH ? t.substring(0, MAX_TITLE_LENGTH) : t;
    }
}
