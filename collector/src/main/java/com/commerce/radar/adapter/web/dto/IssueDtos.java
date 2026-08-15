package com.commerce.radar.adapter.web.dto;

import com.commerce.radar.adapter.persistence.LogPaths;
import com.commerce.radar.adapter.persistence.RunSummary;
import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import com.commerce.radar.adapter.tail.HybrisLogLocator;
import com.commerce.radar.adapter.tail.TailStatus;
import com.commerce.radar.parser.model.LogKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IssueDtos {

    private IssueDtos() {
    }

    @Schema(description = "Grouped issue keyed by fingerprint")
    public record IssueResponse(
            @Schema(example = "NullPointerException@com.yourcompany.facades.impl.DefaultCartFacade.addToCart")
            String fingerprint,
            String title,
            @Schema(example = "ERROR")
            String level,
            @Schema(example = "OCC")
            String kind,
            long count,
            Instant firstSeen,
            Instant lastSeen,
            boolean hasCustomFrame,
            boolean muted,
            String lastMessage,
            Map<String, String> lastBusinessIds,
            @Schema(example = "CONSOLE")
            String logKind,
            String logPath
    ) {
        public static IssueResponse from(StoredIssue issue) {
            String path = issue.lastLogPath();
            LogKind source = LogKind.fromFileName(fileName(path));
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
                    issue.lastBusinessIds(),
                    source.interesting() ? source.name() : "",
                    path
            );
        }
    }

    @Schema(description = "One persisted WARN/ERROR event")
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
            String logKind,
            boolean pinned,
            Instant startedAt,
            Instant lastLineAt,
            boolean live,
            String mode,
            long linesRead,
            long eventsPersisted,
            String lastLine,
            String message,
            String customPackagePrefix,
            List<ActiveSourceResponse> sources,
            List<Long> activeRunIds
    ) {
        public static RunStatusResponse from(TailStatus status, String prefix) {
            String path = status.getLogPath();
            String kind = status.getLogKind();
            if (kind == null || kind.isBlank()) {
                kind = LogKind.fromFileName(fileName(path)).name();
            }
            List<ActiveSourceResponse> sources = status.getSources().stream()
                    .map(item -> new ActiveSourceResponse(item.runId(), item.kind(), item.path(), item.fileName()))
                    .toList();
            return new RunStatusResponse(
                    status.getRunId(),
                    status.getHybrisHome(),
                    path,
                    kind,
                    status.isPinned(),
                    status.getStartedAt(),
                    status.getLastLineAt(),
                    status.isLive(),
                    status.getMode(),
                    status.getLinesRead(),
                    status.getEventsPersisted(),
                    truncate(status.getLastLine(), 240),
                    status.getMessage(),
                    prefix,
                    sources,
                    status.getActiveRunIds()
            );
        }
    }

    public record ActiveSourceResponse(long runId, String kind, String path, String fileName) {
    }

    public record RunSummaryResponse(
            long id,
            String hybrisHome,
            String logPath,
            String logKind,
            Instant startedAt,
            Instant endedAt,
            String mode,
            long eventCount,
            long issueCount,
            boolean current
    ) {
        public static RunSummaryResponse from(RunSummary run, long currentRunId) {
            return from(run, currentRunId > 0 && run.id() == currentRunId);
        }

        public static RunSummaryResponse from(RunSummary run, boolean current) {
            return new RunSummaryResponse(
                    run.id(),
                    run.hybrisHome(),
                    run.logPath(),
                    LogKind.fromFileName(fileName(run.logPath())).name(),
                    run.startedAt(),
                    run.endedAt(),
                    run.mode(),
                    run.eventCount(),
                    run.issueCount(),
                    current
            );
        }
    }

    @Schema(description = "A Hybris log file Radar can open")
    public record LogSourceResponse(
            String kind,
            String path,
            String fileName,
            long sizeBytes,
            Instant lastModified,
            boolean current
    ) {
        public static LogSourceResponse from(HybrisLogLocator.DiscoveredLog item, String currentPath) {
            String path = item.path().toAbsolutePath().normalize().toString();
            boolean current = !currentPath.isBlank()
                    && LogPaths.normalize(path).equals(LogPaths.normalize(currentPath));
            return new LogSourceResponse(
                    item.kind().name(),
                    path,
                    item.fileName(),
                    item.sizeBytes(),
                    item.lastModifiedAt(),
                    current
            );
        }
    }

    @Schema(description = "Point the tailer at a Hybris log file")
    public record OpenLogRequest(
            @Schema(description = "Absolute path to console, catalina, wrapper, or ant.log",
                    example = "D:/hybris/hybris/log/tomcat/console-20260809.log")
            String path,
            @Schema(description = "true = read from the start, false = tail from EOF")
            boolean replay
    ) {
    }

    @Schema(description = "Mute is per fingerprint and global")
    public record MuteRequest(
            @Schema(description = "true mutes, false unmutes")
            boolean muted
    ) {
    }

    public record NotifySettingsResponse(boolean enabled, boolean tabHidden, boolean windowsToast) {
    }

    public record NotifyEnabledRequest(
            @Schema(description = "true turns the header bell on")
            boolean enabled
    ) {
    }

    public record NotifyPresenceRequest(
            @Schema(description = "true when the Radar window is unfocused or the tab is hidden")
            boolean hidden
    ) {
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String value = path.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        return slash < 0 ? value : value.substring(slash + 1);
    }
}
