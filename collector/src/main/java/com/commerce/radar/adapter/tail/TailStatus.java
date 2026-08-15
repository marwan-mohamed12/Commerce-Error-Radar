package com.commerce.radar.adapter.tail;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TailStatus {

    private volatile long runId;
    private volatile String hybrisHome = "";
    private volatile String logPath = "";
    private volatile String logKind = "";
    private volatile boolean pinned;
    private volatile String mode = "IDLE";
    private volatile Instant startedAt;
    private volatile Instant lastLineAt;
    private volatile boolean live;
    private volatile String lastLine = "";
    private final AtomicLong linesRead = new AtomicLong();
    private final AtomicLong eventsPersisted = new AtomicLong();
    private volatile String message = "Waiting for a Hybris log…";
    private volatile List<ActiveSource> sources = List.of();
    private volatile List<Long> activeRunIds = List.of();

    public record ActiveSource(long runId, String kind, String path, String fileName) {
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public String getHybrisHome() {
        return hybrisHome;
    }

    public void setHybrisHome(String hybrisHome) {
        this.hybrisHome = hybrisHome == null ? "" : hybrisHome;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath == null ? "" : logPath;
    }

    public String getLogKind() {
        return logKind;
    }

    public void setLogKind(String logKind) {
        this.logKind = logKind == null ? "" : logKind;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastLineAt() {
        return lastLineAt;
    }

    public void setLastLineAt(Instant lastLineAt) {
        this.lastLineAt = lastLineAt;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    public String getLastLine() {
        return lastLine;
    }

    public void setLastLine(String lastLine) {
        this.lastLine = lastLine == null ? "" : lastLine;
    }

    public long getLinesRead() {
        return linesRead.get();
    }

    public void incrementLines() {
        linesRead.incrementAndGet();
    }

    public long getEventsPersisted() {
        return eventsPersisted.get();
    }

    public void incrementEvents() {
        eventsPersisted.incrementAndGet();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public List<ActiveSource> getSources() {
        return sources;
    }

    public void setSources(List<ActiveSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public List<Long> getActiveRunIds() {
        return activeRunIds;
    }

    public void setActiveRunIds(List<Long> activeRunIds) {
        this.activeRunIds = activeRunIds == null ? List.of() : List.copyOf(activeRunIds);
    }
}
