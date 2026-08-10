package com.commerce.radar.tail;

import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.ingest.EventIngestService;
import com.commerce.radar.ingest.LiveEventHub;
import com.commerce.radar.parser.HybrisLogParser;
import com.commerce.radar.parser.ParsedEvent;
import com.commerce.radar.store.RadarRepository;
import com.commerce.radar.store.StoredRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the newest {@code console-YYYYMMDD.log} from EOF and follows rotation.
 * Does not wrap {@code hybrisserver.bat}; the useful Windows stream is the log file.
 */
@Component
public class ConsoleLogTailer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogTailer.class);

    private final RadarProperties properties;
    private final RadarRepository repository;
    private final EventIngestService ingest;
    private final LiveEventHub hub;
    private final TailStatus status = new TailStatus();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    private HybrisLogParser parser;
    private LineRingBuffer ring;
    private StoredRun currentRun;
    private Path currentFile;
    private long filePointer;
    private boolean printRawLines = false;

    public ConsoleLogTailer(
            RadarProperties properties,
            RadarRepository repository,
            EventIngestService ingest,
            LiveEventHub hub
    ) {
        this.properties = properties;
        this.repository = repository;
        this.ingest = ingest;
        this.hub = hub;
    }

    public TailStatus status() {
        return status;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        parser = new HybrisLogParser(properties.getCustomPackagePrefix());
        ring = new LineRingBuffer(properties.getRingBufferSize());
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hybris-log-tailer");
            t.setDaemon(true);
            return t;
        });
        Path hybris = properties.resolvedHybrisHome();
        if (hybris != null) {
            int removed = repository.deleteDemoData();
            if (removed > 0) {
                log.info("Removed leftover DEMO issues ({}); radar.hybris-home is set", removed);
            }
        }
        LogSourceResolver.Decision decision = LogSourceResolver.resolve(hybris, sampleLogCandidates());
        status.setMode(decision.mode());
        status.setMessage(decision.message());
        if (hybris != null) {
            status.setHybrisHome(hybris.toString());
        }
        log.info("{}", decision.message());
        if (decision.file() != null) {
            boolean fromEnd = properties.isTailFromEnd() && decision.live();
            open(decision.file(), fromEnd);
        } else if (decision.live()) {
            log.warn("Hybris home {} has no console-*.log yet; will keep watching", hybris);
        } else {
            log.warn("{}", decision.message());
        }
        long interval = Math.max(200L, properties.getPollIntervalMs());
        executor.scheduleWithFixedDelay(this::tick, 0, interval, TimeUnit.MILLISECONDS);
        log.info("Tailer started (prefix={}, interval={}ms)", properties.getCustomPackagePrefix(), interval);
    }

    @Override
    public void stop() {
        running.set(false);
        flushParser();
        if (currentRun != null) {
            repository.endRun(currentRun.id());
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        status.setLive(false);
        log.info("Tailer stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public synchronized void openFile(Path path, boolean fromEnd) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Log file not found: " + path);
        }
        flushParser();
        status.setMode(fromEnd ? "LIVE" : "REPLAY");
        open(path, fromEnd);
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        try {
            maybeRotate();
            if (currentFile != null) {
                readAvailable();
            }
        } catch (Exception e) {
            log.warn("Tail tick failed: {}", e.getMessage());
            status.setMessage("Tail error: " + e.getMessage());
        }
    }

    private void maybeRotate() {
        Path newest = resolveNewest();
        if (newest == null) {
            return;
        }
        if (currentFile == null) {
            open(newest, true);
            return;
        }
        try {
            if (!Files.isSameFile(newest, currentFile)) {
                log.info("Log rotated {} -> {}", currentFile.getFileName(), newest.getFileName());
                flushParser();
                open(newest, false);
            }
        } catch (IOException e) {
            if (!newest.toAbsolutePath().normalize().equals(currentFile.toAbsolutePath().normalize())) {
                flushParser();
                open(newest, false);
            }
        }
    }

    private synchronized void open(Path file, boolean fromEnd) {
        currentFile = file.toAbsolutePath().normalize();
        try {
            long size = Files.size(currentFile);
            filePointer = fromEnd ? size : 0L;
        } catch (IOException e) {
            filePointer = 0L;
        }
        Path homePath = properties.resolvedHybrisHome();
        String home = homePath == null ? "" : homePath.toString();
        String mode = status.getMode() == null || "IDLE".equals(status.getMode())
                ? (fromEnd ? "LIVE" : "REPLAY")
                : status.getMode();
        StoredRun next = repository.findOrOpenRun(home, currentFile, mode);
        if (currentRun != null && currentRun.id() != next.id()) {
            repository.endRun(currentRun.id());
        }
        currentRun = next;
        status.setRunId(currentRun.id());
        status.setHybrisHome(home);
        status.setLogPath(currentFile.toString());
        status.setMode(mode);
        status.setStartedAt(currentRun.startedAt() == null ? Instant.now() : currentRun.startedAt());
        status.setLive(true);
        status.setMessage("Tailing " + currentFile.getFileName());
        parser = new HybrisLogParser(properties.getCustomPackagePrefix());
        ring = new LineRingBuffer(properties.getRingBufferSize());
        log.info("Tailing {} from {} ({})", currentFile, fromEnd ? "EOF" : "start", mode);
        hub.publishStatus(statusSnapshot());
    }

    private synchronized void readAvailable() throws IOException {
        if (!Files.isRegularFile(currentFile)) {
            return;
        }
        long size = Files.size(currentFile);
        if (size < filePointer) {
            // truncated
            filePointer = 0L;
        }
        if (size == filePointer) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(currentFile.toFile(), "r")) {
            raf.seek(filePointer);
            String line;
            while ((line = readLine(raf)) != null) {
                handleLine(line);
            }
            filePointer = raf.getFilePointer();
        }
    }

    private void handleLine(String line) {
        status.incrementLines();
        status.setLastLine(line);
        status.setLastLineAt(Instant.now());
        ring.add(line);
        if (printRawLines) {
            System.out.println(line);
        }
        parser.accept(line).ifPresent(this::onEvent);
    }

    private void flushParser() {
        if (parser == null) {
            return;
        }
        parser.flush().ifPresent(this::onEvent);
    }

    private void onEvent(ParsedEvent event) {
        String context = ring.contextBefore(event.rawText(), properties.getContextLines());
        if (currentRun == null) {
            return;
        }
        var issue = ingest.ingest(currentRun.id(), event, context);
        if (issue != null) {
            status.incrementEvents();
            status.setMessage(issue.title());
        }
    }

    private List<Path> sampleLogCandidates() {
        Path configured = properties.getSampleLogsDir();
        Path cwd = Path.of("").toAbsolutePath();
        return List.of(
                configured,
                cwd.resolve("sample-logs"),
                cwd.resolve("..").resolve("sample-logs"),
                cwd.getParent() == null ? null : cwd.getParent().resolve("sample-logs")
        );
    }

    private Path resolveNewest() {
        Path hybris = properties.resolvedHybrisHome();
        if (hybris == null) {
            return currentFile;
        }
        List<Path> dirs = ConsoleLogLocator.tomcatLogDirs(hybris);
        return ConsoleLogLocator.newestIn(dirs.toArray(Path[]::new)).orElse(currentFile);
    }

    private Object statusSnapshot() {
        return status;
    }

    /**
     * RandomAccessFile.readLine() decodes as ISO-8859-1. Re-decode as UTF-8 bytes we already have
     * by reading manually so stacks with special characters survive.
     */
    private static String readLine(RandomAccessFile raf) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        boolean sawContent = false;
        while ((b = raf.read()) != -1) {
            sawContent = true;
            if (b == '\n') {
                break;
            }
            if (b == '\r') {
                long here = raf.getFilePointer();
                int next = raf.read();
                if (next != '\n' && next != -1) {
                    raf.seek(here);
                }
                break;
            }
            sb.append((char) (b & 0xFF));
        }
        if (!sawContent) {
            return null;
        }
        byte[] latin = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        return new String(latin, StandardCharsets.UTF_8);
    }

    static int compareByMtime(Path a, Path b) {
        return Long.compare(ConsoleLogLocator.lastModified(a), ConsoleLogLocator.lastModified(b));
    }

    public void setPrintRawLines(boolean printRawLines) {
        this.printRawLines = printRawLines;
    }

    public List<Path> watchedLogs() {
        Path hybris = properties.resolvedHybrisHome();
        if (hybris == null) {
            return List.of();
        }
        List<Path> found = new ArrayList<>();
        for (Path dir : ConsoleLogLocator.tomcatLogDirs(hybris)) {
            found.addAll(ConsoleLogLocator.listConsoleLogs(dir));
        }
        return found;
    }
}
