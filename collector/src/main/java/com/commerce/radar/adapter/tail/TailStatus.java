package com.commerce.radar.adapter.tail;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public final class TailStatus {

    private volatile long runId;
    private volatile String hybrisHome = "";
    private volatile String logPath = "";
    private volatile String mode = "IDLE";
    private volatile Instant startedAt;
    private volatile Instant lastLineAt;
    private volatile boolean live;
    private volatile String lastLine = "";
    private final AtomicLong linesRead = new AtomicLong();
    private final AtomicLong eventsPersisted = new AtomicLong();
    private volatile String message = "Waiting for a console log…";

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
}
