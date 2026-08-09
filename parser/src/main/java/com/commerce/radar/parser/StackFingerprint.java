package com.commerce.radar.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a stable grouping key from exception type + the first "yours" stack frame.
 * Hybris / Spring / Tomcat frames are never used as the primary fingerprint.
 */
public final class StackFingerprint {

    public static final List<String> IGNORE_PREFIXES = List.of(
            "de.hybris.",
            "org.springframework.",
            "org.apache.catalina.",
            "org.apache.tomcat.",
            "org.apache.coyote.",
            "org.apache.jasper.",
            "org.apache.cxf.",
            "java.",
            "javax.",
            "jakarta.servlet.",
            "jdk.",
            "sun.",
            "org.hibernate.",
            "net.sf.cglib.",
            "org.springframework.cglib.",
            "org.eclipse.jetty.",
            "io.undertow."
    );

    private static final Pattern AT_FRAME = Pattern.compile(
            "^\\s*at\\s+(?:(?<module>[\\w.]+)/)?(?<class>[\\w.$]+)\\.(?<method>[\\w$<>-]+)\\((?<file>[^:)]+)?(?::(?<line>\\d+))?\\)\\s*$"
    );

    private final String customPackagePrefix;

    public StackFingerprint(String customPackagePrefix) {
        this.customPackagePrefix = customPackagePrefix == null ? "" : customPackagePrefix.trim();
    }

    public Result compute(String exceptionType, String stackText) {
        String type = (exceptionType == null || exceptionType.isBlank()) ? "Throwable" : exceptionType;
        List<StackFrame> frames = parseFrames(stackText);

        if (!customPackagePrefix.isEmpty()) {
            for (StackFrame frame : frames) {
                if (frame.className().startsWith(customPackagePrefix)) {
                    return new Result(type + "@" + frame.location(), true, frame);
                }
            }
        }

        for (StackFrame frame : frames) {
            if (!isIgnored(frame.className())) {
                return new Result(type + "@" + frame.location(), false, frame);
            }
        }

        return new Result(type + "@hybris", false, null);
    }

    public static List<StackFrame> parseFrames(String stackText) {
        List<StackFrame> frames = new ArrayList<>();
        if (stackText == null || stackText.isBlank()) {
            return frames;
        }
        for (String line : stackText.split("\\R")) {
            Matcher m = AT_FRAME.matcher(line);
            if (m.matches()) {
                frames.add(new StackFrame(
                        m.group("class"),
                        m.group("method"),
                        m.group("file") == null ? "" : m.group("file"),
                        m.group("line") == null ? "" : m.group("line"),
                        line
                ));
            }
        }
        return frames;
    }

    public static boolean isIgnored(String className) {
        if (className == null || className.isBlank()) {
            return true;
        }
        String c = className;
        int slash = c.indexOf('/');
        if (slash > 0) {
            c = c.substring(slash + 1);
        }
        for (String prefix : IGNORE_PREFIXES) {
            if (className.startsWith(prefix) || c.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHybris(String className) {
        return className != null && className.startsWith("de.hybris.");
    }

    public static boolean isCustom(String className, String prefix) {
        return prefix != null && !prefix.isBlank() && className != null && className.startsWith(prefix);
    }

    public record Result(String fingerprint, boolean hasCustomFrame, StackFrame frame) {
    }
}
