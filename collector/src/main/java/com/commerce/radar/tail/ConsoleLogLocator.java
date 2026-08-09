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
 * Resolves {@code <HYBRIS_HOME>\hybris\log\tomcat\console-*.log}
 * (or {@code log\tomcat} when home already is the {@code hybris} folder).
 */
public final class ConsoleLogLocator {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogLocator.class);

    private ConsoleLogLocator() {
    }

    /**
     * Candidate Tomcat console directories under a Hybris home, in check order.
     * Does not require the directories to exist yet (Hybris creates them on first boot).
     */
    public static List<Path> tomcatLogDirs(Path hybrisHome) {
        if (hybrisHome == null || hybrisHome.toString().isBlank()) {
            return List.of();
        }
        Path home = hybrisHome.toAbsolutePath().normalize();
        List<Path> dirs = new ArrayList<>();
        addDir(dirs, home.resolve("log").resolve("tomcat"));
        addDir(dirs, home.resolve("hybris").resolve("log").resolve("tomcat"));
        addDir(dirs, home.resolve("bin").resolve("platform").resolve("tomcat").resolve("logs"));
        addDir(dirs, home.resolve("hybris").resolve("bin").resolve("platform").resolve("tomcat").resolve("logs"));
        return List.copyOf(dirs);
    }

    /**
     * Best Tomcat log directory: one that already has {@code console*.log},
     * otherwise an existing directory, otherwise the most likely expected path
     * ({@code log/tomcat} when home itself is the {@code hybris} folder).
     */
    public static Path tomcatLogDir(Path hybrisHome) {
        List<Path> dirs = tomcatLogDirs(hybrisHome);
        if (dirs.isEmpty()) {
            return null;
        }
        Path existing = null;
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            if (existing == null) {
                existing = dir;
            }
            if (!listConsoleLogs(dir).isEmpty()) {
                return dir;
            }
        }
        return existing != null ? existing : dirs.getFirst();
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "console*.log")) {
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

    private static void addDir(List<Path> dirs, Path dir) {
        if (dir != null && !dirs.contains(dir)) {
            dirs.add(dir);
        }
    }
}
