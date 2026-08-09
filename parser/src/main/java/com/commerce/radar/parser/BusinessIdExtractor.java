package com.commerce.radar.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls order / product / user / cronjob / catalog identifiers out of log text when present.
 */
public final class BusinessIdExtractor {

    private static final List<Rule> RULES = List.of(
            new Rule("order", Pattern.compile(
                    "(?i)\\b(?:order(?:\\s*(?:code|id|number))?|orderCode|order_code)\\s*[=:]\\s*['\"]?([A-Za-z0-9._-]+)")),
            new Rule("order", Pattern.compile("(?i)\\b(?:for|on)\\s+order\\s+['\"]?([A-Za-z0-9._-]{4,})")),
            new Rule("order", Pattern.compile("(?i)\\bcart\\s+['\"]?([0-9]{6,})")),
            new Rule("product", Pattern.compile(
                    "(?i)\\b(?:product(?:\\s*(?:code|id))?|productCode|product_code)\\s*[=:]\\s*['\"]?([A-Za-z0-9._-]+)")),
            new Rule("product", Pattern.compile("(?i)\\bproduct\\s+['\"]([A-Za-z0-9._-]+)['\"]")),
            new Rule("user", Pattern.compile(
                    "(?i)\\b(?:user(?:\\s*(?:id|name))?|userId|customer(?:Uid|Id)?|customerUid)\\s*[=:]\\s*['\"]?([A-Za-z0-9._@-]+)")),
            new Rule("cronjob", Pattern.compile("(?i)\\bcron\\s*job\\s+([A-Za-z0-9._-]+)")),
            new Rule("cronjob", Pattern.compile("(?i)\\bcronJob(?:Name)?\\s*[=:\\]]\\s*['\"]?([A-Za-z0-9._-]+)")),
            new Rule("cronjob", Pattern.compile("(?i)\\[([A-Za-z0-9._-]*[Cc]ron[Jj]ob[A-Za-z0-9._-]*)\\]")),
            new Rule("cronjob", Pattern.compile("(?i)\\b([A-Za-z0-9._-]*CronJob)\\b")),
            new Rule("impex", Pattern.compile("(?i)(?<![\\w.])([A-Za-z0-9_-]+\\.impex)\\b")),
            new Rule("catalogVersion", Pattern.compile("(?i)\\b([A-Za-z0-9_]+(?:Product)?Catalog):([A-Za-z0-9_]+)\\b"))
    );

    private BusinessIdExtractor() {
    }

    public static Map<String, String> extract(String... texts) {
        Map<String, String> ids = new LinkedHashMap<>();
        String blob = String.join("\n", texts == null ? new String[0] : texts);
        if (blob.isBlank()) {
            return ids;
        }
        for (Rule rule : RULES) {
            if (ids.containsKey(rule.key)) {
                continue;
            }
            Matcher m = rule.pattern.matcher(blob);
            if (m.find()) {
                if ("catalogVersion".equals(rule.key) && m.groupCount() >= 2) {
                    ids.put(rule.key, m.group(1) + ":" + m.group(2));
                } else {
                    String value = m.group(1);
                    if (value != null && !value.isBlank() && !isNoise(value)) {
                        ids.put(rule.key, value);
                    }
                }
            }
        }
        return ids;
    }

    private static boolean isNoise(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.equals("null") || v.equals("true") || v.equals("false") || v.equals("code") || v.equals("id");
    }

    private record Rule(String key, Pattern pattern) {
    }
}
