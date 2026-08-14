package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;

import java.util.Locale;

/**
 * When a persisted event should become a notification.
 * WARN is inbox-only. Muted fingerprints stay quiet.
 */
public final class ErrorNotifyPolicy {

    private ErrorNotifyPolicy() {
    }

    public static boolean shouldNotify(boolean enabled, StoredIssue issue, StoredEvent event) {
        if (!enabled || issue == null || event == null) {
            return false;
        }
        if (issue.muted()) {
            return false;
        }
        String level = event.level() == null || event.level().isBlank() ? issue.level() : event.level();
        return isSevere(level);
    }

    public static boolean isSevere(String level) {
        if (level == null || level.isBlank()) {
            return false;
        }
        String value = level.trim().toUpperCase(Locale.ROOT);
        return "ERROR".equals(value) || "FATAL".equals(value);
    }
}
