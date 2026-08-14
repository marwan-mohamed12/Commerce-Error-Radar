package com.commerce.radar.adapter.persistence;

import java.time.Instant;
import java.util.Map;

public record StoredEvent(
        long id,
        long runId,
        Instant ts,
        String level,
        String logger,
        String thread,
        String message,
        String exception,
        String fingerprint,
        String rawText,
        String contextText,
        String kind,
        boolean hasCustomFrame,
        Map<String, String> businessIds
) {
}
