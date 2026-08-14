package com.commerce.radar.parser.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured view of a single Hybris / Log4j console line.
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
     * Tanuki Service Wrapper line from {@code hybrisserver.bat} / {@code console-YYYYMMDD.log}:
     * {@code INFO   | jvm 1    | main    | 2026/08/10 00:06:17.657 | ERROR [thread] [Logger] msg}
     */
    private static final Pattern WRAPPER = Pattern.compile(
            "^(?:STATUS|INFO|ERROR|WARN|DEBUG)\\s+\\|\\s+[^|]+\\|\\s+[^|]+\\|\\s+"
                    + "(?<wts>\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}[.,]\\d{1,3})\\s+\\|\\s?(?<rest>.*)$"
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

    private static final List<DateTimeFormatter> TS_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss:SSS")
    );

    /**
     * Drop the Tanuki wrapper columns so the Hybris / Log4j payload can be parsed.
     * Idempotent when the line is already a plain log line.
     */
    public static String unwrapWrapper(String raw) {
        if (raw == null) {
            return "";
        }
        String line = stripBom(raw);
        Matcher wrapper = WRAPPER.matcher(line);
        return wrapper.matches() ? wrapper.group("rest") : line;
    }

    public static Optional<LogLine> parseHeader(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String original = stripBom(raw);
        Instant wrapperTs = wrapperTimestamp(original);
        String line = unwrapWrapper(original);
        Matcher ts = TIMESTAMPED.matcher(line);
        if (ts.matches()) {
            Instant parsed = parseTimestamp(ts.group("ts"));
            return Optional.of(new LogLine(
                    parsed != null ? parsed : wrapperTs,
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
                    wrapperTs,
                    bare.group("level"),
                    emptyToBlank(bare.group("thread")),
                    emptyToBlank(bare.group("logger")),
                    emptyToBlank(bare.group("message")),
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
        String line = unwrapWrapper(stripBom(raw));
        if (line.contains(" ERROR ") || line.contains(" WARN ") || line.contains(" FATAL ")) {
            return true;
        }
        for (String level : List.of("ERROR", "WARN", "FATAL")) {
            if (line.startsWith(level + " ") || line.startsWith(level + "\t")) {
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

    private static Instant wrapperTimestamp(String original) {
        Matcher wrapper = WRAPPER.matcher(original);
        if (!wrapper.matches()) {
            return null;
        }
        return parseTimestamp(wrapper.group("wts"));
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
