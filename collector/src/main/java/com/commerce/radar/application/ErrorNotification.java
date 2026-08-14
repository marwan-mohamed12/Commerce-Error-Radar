package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;

import java.util.Locale;

/**
 * Compact ERROR ping for SSE {@code notify} and the Windows toast.
 */
public record ErrorNotification(
        String fingerprint,
        String level,
        String kind,
        String title,
        String message,
        long count
) {
    public static ErrorNotification from(StoredIssue issue, StoredEvent event) {
        String level = first(event == null ? null : event.level(), issue.level(), "ERROR")
                .toUpperCase(Locale.ROOT);
        String kind = first(event == null ? null : event.kind(), issue.kind(), "OTHER");
        String title = first(issue.title(), "ERROR");
        String message = first(event == null ? null : event.message(), issue.lastMessage(), title);
        return new ErrorNotification(
                issue.fingerprint(),
                level,
                kind,
                title,
                clip(message, 240),
                issue.count()
        );
    }

    public String toastTitle() {
        return "Radar · " + level + " · " + kind;
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
