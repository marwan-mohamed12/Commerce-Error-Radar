package com.commerce.radar.parser.model;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Which Hybris log file a path is. Used to discover and label sources;
 * issue classification ({@link IssueKind}) is separate.
 */
public enum LogKind {
    CONSOLE,
    CATALINA,
    WRAPPER,
    ANT,
    LOCALHOST,
    UNKNOWN;

    public static LogKind fromPath(Path path) {
        if (path == null) {
            return UNKNOWN;
        }
        Path name = path.getFileName();
        return fromFileName(name == null ? "" : name.toString());
    }

    public static LogKind fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.contains("access_log") || name.contains("access-log")) {
            return UNKNOWN;
        }
        if (name.equals("ant.log") || name.startsWith("ant.log.")) {
            return ANT;
        }
        if (name.equals("wrapper.log") || name.startsWith("wrapper.log.")) {
            return WRAPPER;
        }
        if (name.startsWith("console") && name.endsWith(".log")) {
            return CONSOLE;
        }
        if (name.startsWith("catalina") && (name.endsWith(".log") || name.endsWith(".out"))) {
            return CATALINA;
        }
        if (name.startsWith("localhost") && name.endsWith(".log")) {
            return LOCALHOST;
        }
        return UNKNOWN;
    }

    public boolean interesting() {
        return this != UNKNOWN;
    }

    public String label() {
        return switch (this) {
            case CONSOLE -> "Console";
            case CATALINA -> "Catalina";
            case WRAPPER -> "Wrapper";
            case ANT -> "Ant";
            case LOCALHOST -> "Localhost";
            case UNKNOWN -> "Log";
        };
    }
}
