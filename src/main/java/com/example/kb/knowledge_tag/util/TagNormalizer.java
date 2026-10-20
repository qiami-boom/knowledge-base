package com.example.kb.knowledge_tag.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 标签规范化：把写法差异压平成可比较的唯一键。
 * 只管"表面形式"（大小写/空格/标点/全角），不管"语义"——同义词归 tag_alias 和 AI 管。
 */
public final class TagNormalizer {

    private static final Pattern PAREN =
            Pattern.compile("[（(\\[【][^）)\\]】]*[）)\\]】]");
    private static final Pattern SHARP_AFTER_LATIN =
            Pattern.compile("(?<=[a-z0-9])#");
    private static final Pattern STRIP = Pattern.compile(
            "[\\s\\-_·.,/\\\\|~!@$%^&*()\\[\\]{}<>?;:'\"`，。、；：！？“”‘’《》【】〔〕]+");

    private TagNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);  // 全角→半角
        s = s.trim().toLowerCase(Locale.ROOT);
        s = PAREN.matcher(s).replaceAll("");                          // 去括号内容
        s = s.replace("++", "pp");                                    // C++ → cpp
        s = SHARP_AFTER_LATIN.matcher(s).replaceAll("sharp");         // C# → csharp
        s = STRIP.matcher(s).replaceAll("");                          // 去空白/连接符/标点
        if (s.isEmpty()) {                                            // 兜底：永不为空
            s = raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        }
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
