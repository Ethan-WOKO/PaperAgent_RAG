package com.yanban.knowledge.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class KnowledgeQueryVariants {

    private static final Pattern LOOKUP_TOKEN = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)+");
    private static final Pattern LONG_TOKEN = Pattern.compile("[a-z0-9_]{8,}");
    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9][a-z0-9_-]{1,}");
    private static final Pattern CJK_OR_LATIN_RUN = Pattern.compile("[\\p{IsHan}a-z0-9_]{2,}");
    private static final int MAX_VARIANTS = 16;
    private static final int CJK_WINDOW_SIZE = 4;
    private static final Set<String> STOP_WORDS = Set.of(
            "answer", "citation", "contains", "exact", "fact", "find", "from", "knowledge",
            "for", "how", "please", "return", "source", "supports", "the", "uploaded",
            "using", "verify", "what", "when", "where", "which", "why"
    );
    private static final Set<String> CJK_STOP_PHRASES = Set.of(
            "根据", "这个", "该", "请", "帮我", "一下", "哪些", "哪", "是什么", "分别",
            "和", "与", "以及", "如果", "我要", "演示文档", "文档里", "文档中的",
            "建议", "优先", "做", "从", "到", "经历", "系统"
    );

    private KnowledgeQueryVariants() {
    }

    static List<String> expand(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        add(variants, normalized);
        addMatches(variants, LOOKUP_TOKEN.matcher(normalized));
        addMatches(variants, LONG_TOKEN.matcher(normalized));
        addMatches(variants, LATIN_TOKEN.matcher(normalized));
        addCjkVariants(variants, normalized);
        return variants.stream().limit(MAX_VARIANTS).toList();
    }

    private static void addMatches(LinkedHashSet<String> variants, Matcher matcher) {
        while (matcher.find()) {
            add(variants, matcher.group());
        }
    }

    private static void add(LinkedHashSet<String> variants, String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("^[\\p{P}\\p{Z}\\s]+|[\\p{P}\\p{Z}\\s]+$", "");
        if (!StringUtils.hasText(cleaned) || STOP_WORDS.contains(cleaned)) {
            return;
        }
        variants.add(cleaned);
    }

    private static void addCjkVariants(LinkedHashSet<String> variants, String normalized) {
        String simplified = normalized.replaceAll("[\\p{P}\\p{Z}\\s]+", " ");
        for (String phrase : CJK_STOP_PHRASES) {
            simplified = simplified.replace(phrase, " ");
        }
        simplified = simplified.replaceAll("\\s+", " ").trim();
        Matcher matcher = CJK_OR_LATIN_RUN.matcher(simplified);
        while (matcher.find()) {
            String segment = matcher.group();
            add(variants, segment);
            if (containsCjk(segment)) {
                addCjkWindows(variants, segment);
            }
        }
    }

    private static void addCjkWindows(LinkedHashSet<String> variants, String segment) {
        if (segment.length() <= CJK_WINDOW_SIZE) {
            return;
        }
        for (int start = 0; start <= segment.length() - CJK_WINDOW_SIZE; start++) {
            add(variants, segment.substring(start, start + CJK_WINDOW_SIZE));
        }
    }

    private static boolean containsCjk(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }
}
