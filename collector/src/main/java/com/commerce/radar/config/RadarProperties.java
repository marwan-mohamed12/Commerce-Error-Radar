package com.commerce.radar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "radar")
public class RadarProperties {

    /**
     * Hybris platform home (the folder that contains {@code hybris/log/tomcat}).
     * Accepts {@code --radar.hybris-home}, {@code --hybris.home}, or env {@code HYBRIS_HOME}.
     */
    private Path hybrisHome;

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

    /** Substring match against raw + context. Matching events are dropped. */
    private List<String> ignorePatterns = new ArrayList<>(List.of(
            "Solr ping",
            "session replication",
            "HAC login",
            "actuator/health"
    ));

    public Path getHybrisHome() {
        return hybrisHome;
    }

    public void setHybrisHome(Path hybrisHome) {
        this.hybrisHome = hybrisHome;
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
}
