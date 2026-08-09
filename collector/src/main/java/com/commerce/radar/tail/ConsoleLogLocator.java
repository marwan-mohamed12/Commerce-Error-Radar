package com.commerce.radar.tail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves {@code <HYBRIS_HOME>\hybris\log\tomcat\console-*.log} (or a demo folder).
 */
public final class ConsoleLogLocator {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogLocator.class);

    private ConsoleLogLocator() {
    }

    public static Path tomcatLogDir(Path hybrisHome) {
        if (hybrisHome == null) {
            return null;
        }
        Path direct = hybrisHome.resolve("hybris").resolve("log").resolve("tomcat");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = hybrisHome.resolve("log").resolve("tomcat");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return direct;
    }

    public static Optional<Path> newestConsoleLog(Path directory) {
        List<Path> matches = listConsoleLogs(directory);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    public static List<Path> listConsoleLogs(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "console-*.log")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    matches.add(path);
                }
            }
        } catch (IOException e) {
            log.warn("Cannot list console logs in {}: {}", directory, e.getMessage());
            return List.of();
        }
        matches.sort(Comparator
                .comparingLong(ConsoleLogLocator::lastModified)
                .thenComparing(path -> path.getFileName().toString())
                .reversed());
        return matches;
    }

    public static Optional<Path> newestIn(Path... directories) {
        Path best = null;
        long bestMod = Long.MIN_VALUE;
        for (Path dir : directories) {
            Optional<Path> candidate = newestConsoleLog(dir);
            if (candidate.isEmpty()) {
                continue;
            }
            long mod = lastModified(candidate.get());
            if (best == null || mod > bestMod) {
                best = candidate.get();
                bestMod = mod;
            }
        }
        return Optional.ofNullable(best);
    }

    public static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
