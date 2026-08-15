package com.commerce.radar.parser.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One closed WARN/ERROR event parsed from a Hybris / Spring / Ant / Catalina log.
 */
public record ParsedEvent(
        Instant timestamp,
        String level,
        String thread,
        String logger,
        String message,
        String exceptionType,
        String stackText,
        String rawText,
        String fingerprint,
        boolean hasCustomFrame,
        IssueKind kind,
        Map<String, String> businessIds
) {
    public ParsedEvent {
        level = level == null ? "ERROR" : level;
        thread = thread == null ? "" : thread;
        logger = logger == null ? "" : logger;
        message = message == null ? "" : message;
        exceptionType = exceptionType == null ? "" : exceptionType;
        stackText = stackText == null ? "" : stackText;
        rawText = rawText == null ? "" : rawText;
        fingerprint = fingerprint == null ? "" : fingerprint;
        kind = kind == null ? IssueKind.OTHER : kind;
        businessIds = businessIds == null ? Map.of() : Map.copyOf(businessIds);
    }

    public ParsedEvent withBusinessIds(Map<String, String> ids) {
        return new ParsedEvent(
                timestamp, level, thread, logger, message, exceptionType, stackText, rawText,
                fingerprint, hasCustomFrame, kind, Objects.requireNonNullElse(ids, Map.of())
        );
    }

    public ParsedEvent withKind(IssueKind newKind) {
        return new ParsedEvent(
                timestamp, level, thread, logger, message, exceptionType, stackText, rawText,
                fingerprint, hasCustomFrame, newKind, businessIds
        );
    }
}
