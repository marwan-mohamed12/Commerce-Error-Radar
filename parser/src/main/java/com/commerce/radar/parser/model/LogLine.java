package com.commerce.radar.parser.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured view of a single Hybris / Log4j / Ant / Catalina line.
 */
public record LogLine(
        Instant timestamp,
        String level,
        String thread,
        String logger,
        String message,
        String raw
) {
    /**
     * Tanuki 4-column line from {@code hybrisserver.bat} / {@code console-YYYYMMDD.log}:
     * {@code INFO   | jvm 1    | main    | 2026/08/10 00:06:17.657 | ERROR [thread] [Logger] msg}
     */
    private static final Pattern WRAPPER_LONG = Pattern.compile(
            "^(?<wlevel>STATUS|INFO|ERROR|WARN|DEBUG)\\s+\\|\\s+[^|]+\\|\\s+[^|]+\\|\\s+"
                    + "(?<wts>\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}[.,]\\d{1,3})\\s+\\|\\s?(?<rest>.*)$"
    );

    /**
     * Tanuki 3-column line from {@code wrapper.log}:
     * {@code ERROR  | wrapper  | 2026/08/10 00:06:18.000 | JVM exited unexpectedly.}
     */
    private static final Pattern WRAPPER_SHORT = Pattern.compile(
            "^(?<wlevel>STATUS|INFO|ERROR|WARN|DEBUG)\\s+\\|\\s+[^|]+\\|\\s+"
                    + "(?<wts>\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}[.,]\\d{1,3})\\s+\\|\\s?(?<rest>.*)$"
    );

    /** One or more Ant task prefixes: {@code [java] }, {@code [javac] }. */
    private static final Pattern ANT_PREFIX = Pattern.compile(
            "^(?:\\s*\\[[A-Za-z][A-Za-z0-9_-]*\\]\\s+)+"
    );

    private static final Pattern TIMESTAMPED = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[,.]\\d{1,3}|\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2}[,:]\\d{1,3})\\s+"
                    + "(?<level>ERROR|WARN|INFO|DEBUG|TRACE|FATAL)\\s+"
                    + "(?:\\[(?<thread>[^\\]]*)\\]\\s+)?"
                    + "(?:\\[(?<logger>[^\\]]*)\\]\\s+)?"
                    + "(?<message>.*)$"
    );

    private static final Pattern BARE = Pattern.compile(
            "^(?<level>ERROR|WARN|INFO|DEBUG|TRACE|FATAL)\\s+"
                    + "(?:\\[(?<thread>[^\\]]*)\\]\\s+)?"
                    + "(?:\\[(?<logger>[^\\]]*)\\]\\s+)?"
                    + "(?<message>.*)$"
    );

    /** Tomcat JULI one-liner: {@code 15-Aug-2026 10:22:05.123 SEVERE [main] org.apache.catalina... msg} */
    private static final Pattern CATALINA = Pattern.compile(
            "^(?<ts>\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,3})?)\\s+"
                    + "(?<level>SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST)\\s+"
                    + "\\[(?<thread>[^\\]]*)\\]\\s+"
                    + "(?<logger>\\S+)\\s+"
                    + "(?<message>.*)$"
    );

    /** Two-line JUL: {@code SEVERE: Context startup failed} */
    private static final Pattern JUL_LEVEL = Pattern.compile(
            "^(?<level>SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST):\\s+(?<message>.*)$"
    );

    private static final Pattern BUILD_RESULT = Pattern.compile(
            "^BUILD (?<result>FAILED|SUCCESSFUL)\\b(?<message>.*)$"
    );

    private static final Pattern BUILDFILE = Pattern.compile(
            "^Buildfile:\\s+(?<message>.*)$"
    );

    private static final Pattern JAVAC_ERROR = Pattern.compile(
            "^(?<file>\\S+\\.java):(?<jline>\\d+):\\s+error:\\s+(?<message>.*)$"
    );

    private static final List<DateTimeFormatter> TS_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss:SSS"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss.SSS", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss,SSS", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH)
    );

    /**
     * Drop Tanuki wrapper columns and Ant {@code [task]} prefixes so the payload can be parsed.
     * Idempotent when the line is already a plain log line.
     */
    public static String unwrapWrapper(String raw) {
        return normalizeLine(raw);
    }

    /**
     * Strip wrapper columns and Ant task prefixes. Does not invent a header.
     */
    public static String normalizeLine(String raw) {
        if (raw == null) {
            return "";
        }
        String line = stripBom(raw);
        WrapperBits wrapper = matchWrapper(line);
        if (wrapper != null) {
            line = wrapper.rest;
        }
        return stripAntPrefix(line);
    }

    public static Optional<LogLine> parseHeader(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String original = stripBom(raw);
        WrapperBits wrapper = matchWrapper(original);
        Instant wrapperTs = wrapper == null ? null : wrapper.timestamp;
        String line = stripAntPrefix(wrapper == null ? original : wrapper.rest);

        Optional<LogLine> structured = parseStructured(line, wrapperTs);
        if (structured.isPresent()) {
            return structured;
        }
        if (wrapper != null && wrapper.errorOrWarn() && !wrapper.rest.isBlank()) {
            return Optional.of(new LogLine(
                    wrapperTs,
                    wrapper.normalizedLevel(),
                    "wrapper",
                    "wrapper",
                    wrapper.rest.strip(),
                    line.isBlank() ? wrapper.rest.strip() : line
            ));
        }
        return Optional.empty();
    }

    private static Optional<LogLine> parseStructured(String line, Instant fallbackTs) {
        Matcher ts = TIMESTAMPED.matcher(line);
        if (ts.matches()) {
            Instant parsed = parseTimestamp(ts.group("ts"));
            return Optional.of(new LogLine(
                    parsed != null ? parsed : fallbackTs,
                    ts.group("level"),
                    emptyToBlank(ts.group("thread")),
                    emptyToBlank(ts.group("logger")),
                    emptyToBlank(ts.group("message")),
                    line
            ));
        }
        Matcher bare = BARE.matcher(line);
        if (bare.matches()) {
            return Optional.of(new LogLine(
                    fallbackTs,
                    bare.group("level"),
                    emptyToBlank(bare.group("thread")),
                    emptyToBlank(bare.group("logger")),
                    emptyToBlank(bare.group("message")),
                    line
            ));
        }
        Matcher catalina = CATALINA.matcher(line);
        if (catalina.matches()) {
            Instant parsed = parseTimestamp(catalina.group("ts"));
            return Optional.of(new LogLine(
                    parsed != null ? parsed : fallbackTs,
                    mapJulLevel(catalina.group("level")),
                    emptyToBlank(catalina.group("thread")),
                    emptyToBlank(catalina.group("logger")),
                    emptyToBlank(catalina.group("message")),
                    line
            ));
        }
        Matcher jul = JUL_LEVEL.matcher(line);
        if (jul.matches()) {
            return Optional.of(new LogLine(
                    fallbackTs,
                    mapJulLevel(jul.group("level")),
                    "",
                    "catalina",
                    emptyToBlank(jul.group("message")),
                    line
            ));
        }
        Matcher build = BUILD_RESULT.matcher(line);
        if (build.matches()) {
            boolean failed = "FAILED".equals(build.group("result"));
            String extra = emptyToBlank(build.group("message")).strip();
            String message = extra.isBlank() ? "BUILD " + build.group("result") : extra;
            return Optional.of(new LogLine(
                    fallbackTs,
                    failed ? "ERROR" : "INFO",
                    "ant",
                    "ant",
                    message,
                    line
            ));
        }
        Matcher buildfile = BUILDFILE.matcher(line);
        if (buildfile.matches()) {
            return Optional.of(new LogLine(
                    fallbackTs,
                    "INFO",
                    "ant",
                    "ant",
                    emptyToBlank(buildfile.group("message")),
                    line
            ));
        }
        Matcher javac = JAVAC_ERROR.matcher(line);
        if (javac.matches()) {
            String file = javac.group("file");
            String simple = file;
            int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
            if (slash >= 0 && slash + 1 < file.length()) {
                simple = file.substring(slash + 1);
            }
            return Optional.of(new LogLine(
                    fallbackTs,
                    "ERROR",
                    "javac",
                    "javac",
                    simple + ":" + javac.group("jline") + ": " + emptyToBlank(javac.group("message")),
                    line
            ));
        }
        return Optional.empty();
    }

    public static boolean looksLikeHeader(String raw) {
        return parseHeader(raw).isPresent();
    }

    public static boolean isErrorOrWarn(String raw) {
        if (raw == null) {
            return false;
        }
        // Plan rule: a line containing " ERROR " or " WARN " starts an event.
        // Also accept headers that begin with ERROR/WARN.
        String original = stripBom(raw);
        WrapperBits wrapper = matchWrapper(original);
        if (wrapper != null && wrapper.errorOrWarn()) {
            return true;
        }
        String line = normalizeLine(original);
        if (line.contains(" ERROR ") || line.contains(" WARN ") || line.contains(" FATAL ")
                || line.contains(" SEVERE ") || line.contains(" WARNING ")) {
            return true;
        }
        if (line.startsWith("BUILD FAILED")) {
            return true;
        }
        if (line.contains(": error:")) {
            return true;
        }
        for (String level : List.of("ERROR", "WARN", "FATAL", "SEVERE", "WARNING")) {
            if (line.startsWith(level + " ") || line.startsWith(level + "\t") || line.startsWith(level + ":")) {
                return true;
            }
        }
        return false;
    }

    public boolean isErrorOrWarnLevel() {
        return "ERROR".equals(level) || "WARN".equals(level) || "FATAL".equals(level);
    }

    public static boolean isStackContinuation(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("at ")) {
            return true;
        }
        if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) {
            return true;
        }
        if (trimmed.startsWith("...")) {
            return true;
        }
        if (trimmed.matches(".*\\.\\.\\.\\s+\\d+\\s+more\\s*")) {
            return true;
        }
        return looksLikeExceptionTypeLine(trimmed);
    }

    public static boolean looksLikeExceptionTypeLine(String trimmed) {
        return trimmed.matches("[\\w.$]+(?:Exception|Error|Throwable)(?::.*)?");
    }

    public static String extractExceptionType(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Pattern p = Pattern.compile("([A-Za-z_][\\w.$]*?(?:Exception|Error|Throwable))\\b");
        Matcher m = p.matcher(text);
        String lastFramework = "";
        while (m.find()) {
            String name = simpleName(m.group(1));
            String fqcn = m.group(1);
            if (!fqcn.startsWith("de.hybris.") && !fqcn.startsWith("org.springframework.")
                    && !fqcn.startsWith("org.apache.")) {
                return name;
            }
            lastFramework = name;
        }
        return lastFramework;
    }

    public static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }

    private static WrapperBits matchWrapper(String original) {
        Matcher longMatch = WRAPPER_LONG.matcher(original);
        if (longMatch.matches()) {
            return new WrapperBits(
                    longMatch.group("wlevel"),
                    parseTimestamp(longMatch.group("wts")),
                    emptyToBlank(longMatch.group("rest"))
            );
        }
        Matcher shortMatch = WRAPPER_SHORT.matcher(original);
        if (shortMatch.matches()) {
            return new WrapperBits(
                    shortMatch.group("wlevel"),
                    parseTimestamp(shortMatch.group("wts")),
                    emptyToBlank(shortMatch.group("rest"))
            );
        }
        return null;
    }

    private static String stripAntPrefix(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        Matcher matcher = ANT_PREFIX.matcher(line);
        return matcher.find() ? line.substring(matcher.end()) : line;
    }

    private static String mapJulLevel(String jul) {
        if (jul == null) {
            return "INFO";
        }
        return switch (jul) {
            case "SEVERE" -> "ERROR";
            case "WARNING" -> "WARN";
            case "CONFIG", "FINE", "FINER", "FINEST" -> "DEBUG";
            default -> "INFO";
        };
    }

    private record WrapperBits(String level, Instant timestamp, String rest) {
        boolean errorOrWarn() {
            return "ERROR".equals(level) || "WARN".equals(level);
        }

        String normalizedLevel() {
            if ("STATUS".equals(level)) {
                return "INFO";
            }
            return level == null ? "INFO" : level;
        }
    }

    private static Instant parseTimestamp(String rawTs) {
        String normalized = rawTs.replace('T', ' ');
        for (DateTimeFormatter fmt : TS_FORMATS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(rawTs, fmt);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(normalized, fmt);
                    return ldt.atZone(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignored2) {
                    // try next
                }
            }
        }
        return null;
    }

    private static String emptyToBlank(String s) {
        return s == null ? "" : s;
    }

    private static String stripBom(String s) {
        if (s.isEmpty()) {
            return s;
        }
        if (s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

}
