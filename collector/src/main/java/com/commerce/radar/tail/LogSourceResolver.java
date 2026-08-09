package com.commerce.radar.tail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Decides LIVE vs DEMO. Sample logs are used <em>only</em> when Hybris home is empty.
 */
public final class LogSourceResolver {

    private LogSourceResolver() {
    }

    public record Decision(String mode, Path file, Path watchDir, String message) {
        public boolean live() {
            return "LIVE".equals(mode);
        }

        public boolean demo() {
            return "DEMO".equals(mode);
        }
    }

    /**
     * @param hybrisHome configured home, or {@code null}/blank for DEMO
     * @param sampleLogDirs folders to scan only when {@code hybrisHome} is empty
     */
    public static Decision resolve(Path hybrisHome, List<Path> sampleLogDirs) {
        if (hybrisHome != null && !hybrisHome.toString().isBlank()) {
            List<Path> dirs = ConsoleLogLocator.tomcatLogDirs(hybrisHome);
            Optional<Path> newest = ConsoleLogLocator.newestIn(dirs.toArray(Path[]::new));
            Path watch = ConsoleLogLocator.tomcatLogDir(hybrisHome);
            if (newest.isPresent()) {
                Path file = newest.get();
                return new Decision(
                        "LIVE",
                        file,
                        watch,
                        "LIVE tailing " + file.toAbsolutePath().normalize()
                );
            }
            String watched = watch == null ? hybrisHome.toString() : watch.toString();
            return new Decision(
                    "LIVE",
                    null,
                    watch,
                    "Watching " + watched + " for console-*.log"
            );
        }
        Path sample = findSampleLog(sampleLogDirs);
        if (sample != null) {
            return new Decision(
                    "DEMO",
                    sample,
                    null,
                    "DEMO replaying sample log " + sample.toAbsolutePath().normalize()
            );
        }
        return new Decision(
                "IDLE",
                null,
                null,
                "No console-*.log found. Set radar.hybris-home or HYBRIS_HOME."
        );
    }

    static Path findSampleLog(List<Path> sampleLogDirs) {
        if (sampleLogDirs == null) {
            return null;
        }
        for (Path samples : sampleLogDirs) {
            if (samples == null || !Files.isDirectory(samples)) {
                continue;
            }
            Optional<Path> demo = ConsoleLogLocator.newestConsoleLog(samples);
            if (demo.isEmpty()) {
                try (var stream = Files.list(samples)) {
                    demo = stream
                            .filter(p -> Files.isRegularFile(p))
                            .filter(p -> p.getFileName().toString().endsWith(".log"))
                            .max(ConsoleLogTailer::compareByMtime);
                } catch (IOException ignored) {
                    demo = Optional.empty();
                }
            }
            if (demo.isPresent()) {
                return demo.get();
            }
        }
        return null;
    }
}
