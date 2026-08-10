package com.commerce.radar.parser;

import java.util.Locale;
import java.util.Map;

/**
 * Labels a Hybris event as CronJob / ImpEx / OCC / etc. and builds a scannable title.
 */
public final class IssueClassifier {

    private IssueClassifier() {
    }

    public static IssueKind classify(String logger, String thread, String message, String exceptionType, String rawText) {
        String blob = safe(logger) + "\n" + safe(thread) + "\n" + safe(message) + "\n" + safe(exceptionType) + "\n" + safe(rawText);
        String lower = blob.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "impex", "impexexception", ".impex", "unknown catalog version")) {
            return IssueKind.IMPEX;
        }
        if (containsAny(lower, "cronjob", "cron job", "jobperformable", "abstractjobperformable")) {
            return IssueKind.CRONJOB;
        }
        if (containsAny(lower, "flexiblesearch", "flexible search", "flexiblesearchexception")) {
            return IssueKind.FLEXIBLE_SEARCH;
        }
        if (containsAny(lower, "solr", "solrindexer", "solrfacetsearch", "solrquery")) {
            return IssueKind.SOLR;
        }
        if (containsAny(lower, "interceptor", "validateinterceptor", "prepareinterceptor", "interceptorexception")) {
            return IssueKind.INTERCEPTOR;
        }
        if (containsAny(lower, "modelsavingexception", "modelservice.save", "model save")) {
            return IssueKind.MODEL_SAVE;
        }
        if (containsAny(lower, "commercewebservices", "occ", "/rest/v2", "cartscontroller", "productscontroller",
                "defaultcartfacade", "occinterceptor")) {
            return IssueKind.OCC;
        }
        return IssueKind.OTHER;
    }

    public static String title(IssueKind kind, String exceptionType, String message, String logger,
                               StackFingerprint.Result fingerprint, Map<String, String> ids) {
        String shortEx = shortenException(exceptionType);
        String simple = simpleFromFingerprint(fingerprint);

        return switch (kind) {
            case CRONJOB -> {
                String job = firstNonBlank(ids.get("cronjob"), logger, "cronjob");
                yield "CronJob " + stripCronSuffix(job) + " failed — " + shortEx;
            }
            case IMPEX -> {
                String file = firstNonBlank(ids.get("impex"), extractImpex(message), "import");
                String detail = firstNonBlank(trimMessage(message), shortEx);
                yield "ImpEx " + file + " — " + detail;
            }
            case OCC -> withKindPrefix("OCC", simple.isBlank() ? firstNonBlank(logger, "controller") : simple)
                    + " — " + shortEx;
            case FLEXIBLE_SEARCH -> "FlexibleSearch — " + firstNonBlank(trimMessage(message), shortEx);
            case SOLR -> withKindPrefix("Solr", firstNonBlank(trimMessage(message), shortEx));
            case INTERCEPTOR -> withKindPrefix("Interceptor", firstNonBlank(simple, logger)) + " — " + shortEx;
            case MODEL_SAVE -> "Model save — " + shortEx;
            case OTHER -> {
                String head = simple.isBlank() ? firstNonBlank(logger, "Hybris") : simple;
                yield head + " — " + firstNonBlank(shortEx, trimMessage(message));
            }
        };
    }

    private static String simpleFromFingerprint(StackFingerprint.Result result) {
        if (result == null || result.frame() == null) {
            return "";
        }
        String cls = result.frame().className();
        int dot = cls.lastIndexOf('.');
        String simple = dot < 0 ? cls : cls.substring(dot + 1);
        return simple + "." + result.frame().method();
    }

    private static String shortenException(String exceptionType) {
        if (exceptionType == null || exceptionType.isBlank()) {
            return "error";
        }
        String simple = LogLine.simpleName(exceptionType);
        return switch (simple) {
            case "NullPointerException" -> "NPE";
            case "IllegalArgumentException" -> "IAE";
            case "IllegalStateException" -> "ISE";
            default -> simple;
        };
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return "";
        }
        String t = message.replaceAll("\\s+", " ").strip();
        if (t.length() > 80) {
            return t.substring(0, 77) + "...";
        }
        return t;
    }

    private static String extractImpex(String message) {
        if (message == null) {
            return "";
        }
        var m = java.util.regex.Pattern.compile("(?i)(?<![\\w.])([A-Za-z0-9_-]+\\.impex)").matcher(message);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Do not emit {@code OCC OCCConsentLayerFilter…} when the class already starts with the kind.
     */
    static String withKindPrefix(String kindWord, String subject) {
        String body = firstNonBlank(subject, kindWord);
        if (startsWithKindToken(body, kindWord)) {
            return body;
        }
        return kindWord + " " + body;
    }

    static boolean startsWithKindToken(String text, String kindWord) {
        if (text == null || kindWord == null || kindWord.isBlank()) {
            return false;
        }
        String value = text.strip();
        if (value.length() < kindWord.length()) {
            return false;
        }
        if (!value.regionMatches(true, 0, kindWord, 0, kindWord.length())) {
            return false;
        }
        if (value.length() == kindWord.length()) {
            return true;
        }
        char next = value.charAt(kindWord.length());
        return next == ' ' || next == '-' || next == '_' || next == '.' || Character.isUpperCase(next);
    }

    private static String stripCronSuffix(String name) {
        if (name == null) {
            return "job";
        }
        return name.replaceAll("(?i)CronJob$", "");
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
