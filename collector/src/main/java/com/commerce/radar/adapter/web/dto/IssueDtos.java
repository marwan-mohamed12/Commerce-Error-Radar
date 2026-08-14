package com.commerce.radar.adapter.web.dto;

import com.commerce.radar.adapter.persistence.RunSummary;
import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import com.commerce.radar.adapter.tail.TailStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IssueDtos {

    private IssueDtos() {
    }

    public record IssueResponse(
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
            Map<String, String> lastBusinessIds
    ) {
        public static IssueResponse from(StoredIssue issue) {
            return new IssueResponse(
                    issue.fingerprint(),
                    issue.title(),
                    issue.level(),
                    issue.kind(),
                    issue.count(),
                    issue.firstSeen(),
                    issue.lastSeen(),
                    issue.hasCustomFrame(),
                    issue.muted(),
                    issue.lastMessage(),
                    issue.lastBusinessIds()
            );
        }
    }

    public record EventResponse(
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
        public static EventResponse from(StoredEvent event) {
            return new EventResponse(
                    event.id(),
                    event.runId(),
                    event.ts(),
                    event.level(),
                    event.logger(),
                    event.thread(),
                    event.message(),
                    event.exception(),
                    event.fingerprint(),
                    event.rawText(),
                    event.contextText(),
                    event.kind(),
                    event.hasCustomFrame(),
                    event.businessIds()
            );
        }
    }

    public record IssueDetailResponse(IssueResponse issue, List<EventResponse> events) {
    }

    public record RunStatusResponse(
            long id,
            String hybrisHome,
            String logPath,
            Instant startedAt,
            Instant lastLineAt,
            boolean live,
            String mode,
            long linesRead,
            long eventsPersisted,
            String lastLine,
            String message,
            String customPackagePrefix
    ) {
        public static RunStatusResponse from(TailStatus status, String prefix) {
            return new RunStatusResponse(
                    status.getRunId(),
                    status.getHybrisHome(),
                    status.getLogPath(),
                    status.getStartedAt(),
                    status.getLastLineAt(),
                    status.isLive(),
                    status.getMode(),
                    status.getLinesRead(),
                    status.getEventsPersisted(),
                    truncate(status.getLastLine(), 240),
                    status.getMessage(),
                    prefix
            );
        }
    }

    public record RunSummaryResponse(
            long id,
            String hybrisHome,
            String logPath,
            Instant startedAt,
            Instant endedAt,
            String mode,
            long eventCount,
            long issueCount,
            boolean current
    ) {
        public static RunSummaryResponse from(RunSummary run, long currentRunId) {
            return new RunSummaryResponse(
                    run.id(),
                    run.hybrisHome(),
                    run.logPath(),
                    run.startedAt(),
                    run.endedAt(),
                    run.mode(),
                    run.eventCount(),
                    run.issueCount(),
                    run.id() == currentRunId
            );
        }
    }

    public record OpenLogRequest(String path, boolean replay) {
    }

    public record MuteRequest(boolean muted) {
    }

    public record NotifySettingsResponse(boolean enabled, boolean tabHidden, boolean windowsToast) {
    }

    public record NotifyEnabledRequest(boolean enabled) {
    }

    public record NotifyPresenceRequest(boolean hidden) {
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
