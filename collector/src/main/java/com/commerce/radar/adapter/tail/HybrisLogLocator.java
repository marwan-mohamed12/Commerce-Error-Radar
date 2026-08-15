package com.commerce.radar.adapter.tail;

import com.commerce.radar.parser.model.LogKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds Hybris log files of every kind Radar can read: console, catalina,
 * wrapper, ant, localhost (not access logs).
 */
public final class HybrisLogLocator {

    private static final Logger log = LoggerFactory.getLogger(HybrisLogLocator.class);

    private HybrisLogLocator() {
    }

    public record DiscoveredLog(Path path, LogKind kind, long lastModified, long sizeBytes) {
        public Instant lastModifiedAt() {
            return lastModified <= 0L ? null : Instant.ofEpochMilli(lastModified);
        }

        public String fileName() {
            Path name = path.getFileName();
            return name == null ? path.toString() : name.toString();
        }
    }

    /**
     * Candidate log directories under a Hybris home, in check order.
     * Does not require the directories to exist yet.
     */
    public static List<Path> logDirs(Path hybrisHome) {
        if (hybrisHome == null || hybrisHome.toString().isBlank()) {
            return List.of();
        }
        Path home = hybrisHome.toAbsolutePath().normalize();
        List<Path> dirs = new ArrayList<>();
        addDir(dirs, home.resolve("log"));
        addDir(dirs, home.resolve("hybris").resolve("log"));
        addDir(dirs, home.resolve("log").resolve("tomcat"));
        addDir(dirs, home.resolve("hybris").resolve("log").resolve("tomcat"));
        addDir(dirs, home.resolve("bin").resolve("platform").resolve("tomcat").resolve("logs"));
        addDir(dirs, home.resolve("hybris").resolve("bin").resolve("platform").resolve("tomcat").resolve("logs"));
        return List.copyOf(dirs);
    }

    /**
     * Best directory to watch for new files: an existing {@code log} folder
     * (parent of tomcat + ant.log), otherwise the Tomcat folder.
     */
    public static Path watchDir(Path hybrisHome) {
        List<Path> dirs = logDirs(hybrisHome);
        if (dirs.isEmpty()) {
            return null;
        }
        Path existingLog = null;
        Path existingAny = null;
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            if (existingAny == null) {
                existingAny = dir;
            }
            if (dir.getFileName() != null && "log".equalsIgnoreCase(dir.getFileName().toString()) && existingLog == null) {
                existingLog = dir;
            }
            if (!listAll(List.of(dir)).isEmpty() && existingLog == null
                    && dir.getFileName() != null && "log".equalsIgnoreCase(dir.getFileName().toString())) {
                return dir;
            }
        }
        if (existingLog != null) {
            return existingLog;
        }
        Path tomcat = ConsoleLogLocator.tomcatLogDir(hybrisHome);
        if (tomcat != null) {
            return tomcat;
        }
        return existingAny != null ? existingAny : dirs.getFirst();
    }

    public static List<DiscoveredLog> listAll(Path hybrisHome) {
        return listAll(logDirs(hybrisHome));
    }

    public static List<DiscoveredLog> listAll(List<Path> directories) {
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        List<DiscoveredLog> found = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        for (Path dir : directories) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    Path norm = path.toAbsolutePath().normalize();
                    if (!seen.add(norm)) {
                        continue;
                    }
                    LogKind kind = LogKind.fromPath(norm);
                    if (!kind.interesting()) {
                        continue;
                    }
                    found.add(new DiscoveredLog(norm, kind, lastModified(norm), sizeOf(norm)));
                }
            } catch (IOException e) {
                log.warn("Cannot list Hybris logs in {}: {}", dir, e.getMessage());
            }
        }
        found.sort(Comparator
                .comparingLong(DiscoveredLog::lastModified)
                .thenComparing(item -> item.fileName(), String.CASE_INSENSITIVE_ORDER)
                .reversed());
        return found;
    }

    public static Optional<Path> newest(Path hybrisHome) {
        List<DiscoveredLog> all = listAll(hybrisHome);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst().path());
    }

    /**
     * Newest file of each interesting kind. {@link #listAll} is newest-first,
     * so the first hit per kind wins.
     */
    public static Map<LogKind, DiscoveredLog> newestByKind(Path hybrisHome) {
        Map<LogKind, DiscoveredLog> newest = new EnumMap<>(LogKind.class);
        for (DiscoveredLog item : listAll(hybrisHome)) {
            newest.putIfAbsent(item.kind(), item);
        }
        Map<LogKind, DiscoveredLog> ordered = new LinkedHashMap<>();
        for (LogKind kind : List.of(
                LogKind.CONSOLE, LogKind.WRAPPER, LogKind.ANT, LogKind.CATALINA, LogKind.LOCALHOST
        )) {
            DiscoveredLog item = newest.get(kind);
            if (item != null) {
                ordered.put(kind, item);
            }
        }
        return ordered;
    }

    public static Optional<Path> newest(Path... directories) {
        List<DiscoveredLog> all = listAll(directories == null ? List.of() : List.of(directories));
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst().path());
    }

    public static Optional<Path> newestOfKind(Path hybrisHome, LogKind kind, Path exclude) {
        if (kind == null || !kind.interesting()) {
            return Optional.empty();
        }
        Path skip = exclude == null ? null : exclude.toAbsolutePath().normalize();
        for (DiscoveredLog item : listAll(hybrisHome)) {
            if (item.kind() != kind) {
                continue;
            }
            Path path = item.path().toAbsolutePath().normalize();
            if (skip != null && path.equals(skip)) {
                continue;
            }
            return Optional.of(path);
        }
        return Optional.empty();
    }

    public static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
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
