package com.commerce.radar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "radar")
public class RadarProperties {

    /**
     * Hybris platform home (the folder that contains {@code hybris/log/tomcat}
     * or {@code log/tomcat}). Blank / unset → DEMO sample logs.
     * Accepts {@code --radar.hybris-home} or env {@code HYBRIS_HOME}.
     * Stored as a string so an empty value does not become a Path, and so
     * {@code D:/...} in application.properties survives on Windows.
     */
    private String hybrisHome = "";

    /** First stack frame starting with this prefix is the fingerprint. */
    private String customPackagePrefix = "com.yourcompany";

    /** If true, start at EOF. If false, replay the whole current log file. */
    private boolean tailFromEnd = true;

    /** Directory poll + file-length poll interval. */
    private long pollIntervalMs = 500;

    /** Context lines attached when an ERROR/WARN closes. */
    private int contextLines = 30;

    /** Ring buffer of recent lines kept for context. */
    private int ringBufferSize = 100;

    /** Relative or absolute SQLite file. */
    private Path sqlitePath = Path.of("data/radar.db");

    /**
     * Optional folder of sample logs used when Hybris home is missing.
     * Defaults to {@code ../sample-logs} relative to the collector working dir.
     */
    private Path sampleLogsDir = Path.of("../sample-logs");

    /**
     * Default for optional ERROR Windows toasts. The UI bell overrides this
     * and stores the choice in SQLite ({@code settings.notify.enabled}).
     */
    private boolean notifyOnError = false;

    /** Substring match against raw + context. Matching events are dropped. */
    private List<String> ignorePatterns = new ArrayList<>(List.of(
            "Solr ping",
            "session replication",
            "HAC login",
            "actuator/health"
    ));

    public String getHybrisHome() {
        return hybrisHome;
    }

    public void setHybrisHome(String hybrisHome) {
        this.hybrisHome = hybrisHome == null ? "" : hybrisHome.trim();
    }

    /**
     * True only when a Hybris home was actually configured.
     * Empty string, blanks, and {@code --radar.hybris-home=} do not count —
     * those fall through to {@code HYBRIS_HOME} / {@code hybris.home}, then DEMO.
     */
    public boolean hasHybrisHome() {
        return resolvedHybrisHome() != null;
    }

    /**
     * Configured Hybris home, or {@code null} when sample logs should be used.
     */
    public Path resolvedHybrisHome() {
        String raw = firstNonBlank(
                hybrisHome,
                System.getenv("HYBRIS_HOME"),
                System.getProperty("hybris.home"),
                System.getProperty("radar.hybris-home")
        );
        return raw == null ? null : Path.of(raw);
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public String getCustomPackagePrefix() {
        return customPackagePrefix;
    }

    public void setCustomPackagePrefix(String customPackagePrefix) {
        this.customPackagePrefix = customPackagePrefix;
    }

    public boolean isTailFromEnd() {
        return tailFromEnd;
    }

    public void setTailFromEnd(boolean tailFromEnd) {
        this.tailFromEnd = tailFromEnd;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public Path getSqlitePath() {
        return sqlitePath;
    }

    public void setSqlitePath(Path sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    public Path getSampleLogsDir() {
        return sampleLogsDir;
    }

    public void setSampleLogsDir(Path sampleLogsDir) {
        this.sampleLogsDir = sampleLogsDir;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public void setIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    public boolean isNotifyOnError() {
        return notifyOnError;
    }

    public void setNotifyOnError(boolean notifyOnError) {
        this.notifyOnError = notifyOnError;
    }
}
