package com.commerce.radar.adapter.persistence;

import java.nio.file.Path;
import java.util.Locale;

/** One Hybris log file is one session — compare paths in a stable form. */
public final class LogPaths {

    private LogPaths() {
    }

    public static String normalize(Path file) {
        if (file == null) {
            return "";
        }
        return normalize(file.toAbsolutePath().normalize().toString());
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String value = path.strip().replace('\\', '/');
        if (value.length() >= 2 && value.charAt(1) == ':') {
            value = value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
