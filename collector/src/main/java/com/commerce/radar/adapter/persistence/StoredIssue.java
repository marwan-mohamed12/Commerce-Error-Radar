package com.commerce.radar.adapter.persistence;

import java.time.Instant;
import java.util.Map;

public record StoredIssue(
        String fingerprint,
        String title,
        String level,
        String kind,
        long count,
        Instant firstSeen,
        Instant lastSeen,
        boolean hasCustomFrame,
        boolean muted,
        String lastMessage,
        Map<String, String> lastBusinessIds,
        String lastLogPath
) {
    public StoredIssue {
        lastLogPath = lastLogPath == null ? "" : lastLogPath;
    }
}
