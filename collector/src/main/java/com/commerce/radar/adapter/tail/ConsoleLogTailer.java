package com.commerce.radar.adapter.tail;

import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.application.EventIngestService;
import com.commerce.radar.application.LiveEventHub;
import com.commerce.radar.parser.HybrisLogParser;
import com.commerce.radar.parser.model.LogKind;
import com.commerce.radar.parser.model.ParsedEvent;
import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.persistence.StoredRun;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the newest file of each Hybris log kind (console, wrapper, ant,
 * catalina, localhost) in parallel. Does not wrap {@code hybrisserver.bat} or Ant.
 */
@Component
public class ConsoleLogTailer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogTailer.class);

    private static final List<LogKind> LIVE_KINDS = List.of(
            LogKind.CONSOLE, LogKind.WRAPPER, LogKind.ANT, LogKind.CATALINA, LogKind.LOCALHOST
    );

    private final RadarProperties properties;
    private final RadarRepository repository;
    private final EventIngestService ingest;
    private final LiveEventHub hub;
    private final TailStatus status = new TailStatus();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<LogKind, TailHandle> handles = new EnumMap<>(LogKind.class);
    private ScheduledExecutorService executor;
    private TailHandle lastActive;
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
        if (decision.live() && hybris != null) {
            boolean fromEnd = properties.isTailFromEnd();
            openNewestOfEachKind(fromEnd);
            if (handles.isEmpty()) {
                log.warn("Hybris home {} has no Hybris logs yet; will keep watching", hybris);
            }
        } else if (decision.file() != null) {
            openHandle(LogKind.fromPath(decision.file()), decision.file(), false);
        } else {
            log.warn("{}", decision.message());
        }
        publishStatus();
        long interval = Math.max(200L, properties.getPollIntervalMs());
        executor.scheduleWithFixedDelay(this::tick, 0, interval, TimeUnit.MILLISECONDS);
        log.info("Tailer started (prefix={}, interval={}ms, files={})",
                properties.getCustomPackagePrefix(), interval, handles.size());
    }

    @Override
    public void stop() {
        running.set(false);
        for (TailHandle handle : handles.values()) {
            flushHandle(handle);
            if (handle.run != null) {
                repository.endRun(handle.run.id());
            }
        }
        handles.clear();
        lastActive = null;
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
        LogKind kind = LogKind.fromPath(path);
        if (!kind.interesting()) {
            kind = LogKind.CONSOLE;
        }
        status.setMode(fromEnd ? "LIVE" : "REPLAY");
        openHandle(kind, path, fromEnd);
        publishStatus();
    }

    /**
     * Tail the newest file of every log kind.
     */
    public synchronized void followNewest() {
        status.setMode("LIVE");
        openNewestOfEachKind(properties.isTailFromEnd());
        if (handles.isEmpty()) {
            status.setMessage("Watching for console, wrapper, ant, catalina, localhost logs");
        }
        publishStatus();
    }

    public List<Long> activeRunIds() {
        return activeRunIds(null);
    }

    public synchronized List<Long> activeRunIds(String logKind) {
        LogKind wanted = parseKind(logKind);
        List<Long> ids = new ArrayList<>();
        for (LogKind kind : LIVE_KINDS) {
            if (wanted != null && wanted != kind) {
                continue;
            }
            TailHandle handle = handles.get(kind);
            if (handle != null && handle.run != null) {
                ids.add(handle.run.id());
            }
        }
        return ids;
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        try {
            adoptNewFiles();
            for (TailHandle handle : snapshotHandles()) {
                readHandle(handle);
            }
        } catch (Exception e) {
            log.warn("Tail tick failed: {}", e.getMessage());
            status.setMessage("Tail error: " + e.getMessage());
        }
    }

    private synchronized void adoptNewFiles() {
        Path hybris = properties.resolvedHybrisHome();
        if (hybris == null) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<LogKind, HybrisLogLocator.DiscoveredLog> entry
                : HybrisLogLocator.newestByKind(hybris).entrySet()) {
            TailHandle existing = handles.get(entry.getKey());
            Path newest = entry.getValue().path();
            if (existing == null) {
                openHandle(entry.getKey(), newest, true);
                changed = true;
            } else if (!sameFile(existing.file, newest)) {
                log.info("Log rotated {} -> {}", existing.file.getFileName(), newest.getFileName());
                flushHandle(existing);
                openHandle(entry.getKey(), newest, false);
                changed = true;
            }
        }
        if (changed) {
            publishStatus();
        }
    }

    private synchronized void openNewestOfEachKind(boolean fromEnd) {
        Path hybris = properties.resolvedHybrisHome();
        if (hybris == null) {
            return;
        }
        Map<LogKind, HybrisLogLocator.DiscoveredLog> newest = HybrisLogLocator.newestByKind(hybris);
        for (LogKind kind : List.copyOf(handles.keySet())) {
            if (!newest.containsKey(kind)) {
                TailHandle stale = handles.remove(kind);
                flushHandle(stale);
            }
        }
        for (Map.Entry<LogKind, HybrisLogLocator.DiscoveredLog> entry : newest.entrySet()) {
            TailHandle existing = handles.get(entry.getKey());
            Path path = entry.getValue().path();
            if (existing != null && sameFile(existing.file, path)) {
                continue;
            }
            if (existing != null) {
                flushHandle(existing);
            }
            openHandle(entry.getKey(), path, fromEnd);
        }
    }

    private synchronized void openHandle(LogKind kind, Path file, boolean fromEnd) {
        Path normalized = file.toAbsolutePath().normalize();
        TailHandle handle = handles.computeIfAbsent(kind, TailHandle::new);
        handle.file = normalized;
        try {
            handle.pointer = fromEnd ? Files.size(normalized) : 0L;
        } catch (IOException e) {
            handle.pointer = 0L;
        }
        Path homePath = properties.resolvedHybrisHome();
        String home = homePath == null ? "" : homePath.toString();
        String mode = status.getMode() == null || "IDLE".equals(status.getMode())
                ? (fromEnd ? "LIVE" : "REPLAY")
                : status.getMode();
        StoredRun next = repository.findOrOpenRun(home, normalized, mode);
        if (handle.run != null && handle.run.id() != next.id()) {
            repository.endRun(handle.run.id());
        }
        handle.run = next;
        handle.parser = new HybrisLogParser(properties.getCustomPackagePrefix());
        handle.ring = new LineRingBuffer(properties.getRingBufferSize());
        lastActive = handle;
        log.info("Tailing {} from {} ({})", normalized, fromEnd ? "EOF" : "start", mode);
    }

    private synchronized void readHandle(TailHandle handle) throws IOException {
        if (handle.file == null || !Files.isRegularFile(handle.file)) {
            return;
        }
        long size = Files.size(handle.file);
        if (size < handle.pointer) {
            handle.pointer = 0L;
        }
        if (size == handle.pointer) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(handle.file.toFile(), "r")) {
            raf.seek(handle.pointer);
            String line;
            while ((line = readLine(raf)) != null) {
                handleLine(handle, line);
            }
            handle.pointer = raf.getFilePointer();
        }
    }

    private void handleLine(TailHandle handle, String line) {
        status.incrementLines();
        status.setLastLine(line);
        status.setLastLineAt(Instant.now());
        lastActive = handle;
        handle.ring.add(line);
        if (printRawLines) {
            System.out.println(line);
        }
        handle.parser.accept(line).ifPresent(event -> onEvent(handle, event));
    }

    private void flushHandle(TailHandle handle) {
        if (handle == null || handle.parser == null) {
            return;
        }
        handle.parser.flush().ifPresent(event -> onEvent(handle, event));
    }

    private void onEvent(TailHandle handle, ParsedEvent event) {
        String context = handle.ring.contextBefore(event.rawText(), properties.getContextLines());
        if (handle.run == null) {
            return;
        }
        var issue = ingest.ingest(handle.run.id(), event, context);
        if (issue != null) {
            status.incrementEvents();
            status.setMessage(issue.title());
        }
    }

    private void publishStatus() {
        TailHandle focus = lastActive != null ? lastActive : firstHandle();
        Path homePath = properties.resolvedHybrisHome();
        String home = homePath == null ? "" : homePath.toString();
        status.setHybrisHome(home);
        if (focus != null && focus.run != null) {
            status.setRunId(focus.run.id());
            status.setLogPath(focus.file == null ? "" : focus.file.toString());
            status.setLogKind(focus.kind.name());
            status.setStartedAt(focus.run.startedAt() == null ? Instant.now() : focus.run.startedAt());
            status.setLive(true);
            if (status.getMessage() == null || status.getMessage().isBlank()
                    || status.getMessage().startsWith("LIVE")
                    || status.getMessage().startsWith("DEMO")
                    || status.getMessage().startsWith("Watching")
                    || status.getMessage().startsWith("Tailing")) {
                status.setMessage(handles.size() == 1
                        ? "Tailing " + focus.file.getFileName()
                        : "Tailing " + handles.size() + " Hybris logs");
            }
        } else {
            status.setLive("LIVE".equals(status.getMode()) || "DEMO".equals(status.getMode()));
        }
        List<TailStatus.ActiveSource> sources = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (LogKind kind : LIVE_KINDS) {
            TailHandle handle = handles.get(kind);
            if (handle == null || handle.run == null || handle.file == null) {
                continue;
            }
            ids.add(handle.run.id());
            Path name = handle.file.getFileName();
            sources.add(new TailStatus.ActiveSource(
                    handle.run.id(),
                    kind.name(),
                    handle.file.toString(),
                    name == null ? handle.file.toString() : name.toString()
            ));
        }
        status.setSources(sources);
        status.setActiveRunIds(ids);
        status.setPinned(false);
        hub.publishStatus(statusSnapshot());
    }

    private synchronized List<TailHandle> snapshotHandles() {
        return List.copyOf(handles.values());
    }

    private TailHandle firstHandle() {
        for (LogKind kind : LIVE_KINDS) {
            TailHandle handle = handles.get(kind);
            if (handle != null) {
                return handle;
            }
        }
        return null;
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

    private static LogKind parseKind(String logKind) {
        if (logKind == null || logKind.isBlank() || "ALL".equalsIgnoreCase(logKind)) {
            return null;
        }
        try {
            return LogKind.valueOf(logKind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean sameFile(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
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
        for (HybrisLogLocator.DiscoveredLog item : HybrisLogLocator.listAll(hybris)) {
            found.add(item.path());
        }
        return found;
    }

    public List<HybrisLogLocator.DiscoveredLog> discoveredLogs() {
        Path hybris = properties.resolvedHybrisHome();
        if (hybris == null) {
            return List.of();
        }
        return HybrisLogLocator.listAll(hybris);
    }

    private static final class TailHandle {
        private final LogKind kind;
        private Path file;
        private long pointer;
        private HybrisLogParser parser;
        private LineRingBuffer ring;
        private StoredRun run;

        private TailHandle(LogKind kind) {
            this.kind = kind;
        }
    }
}
