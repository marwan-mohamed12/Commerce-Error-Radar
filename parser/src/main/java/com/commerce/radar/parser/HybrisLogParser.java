package com.commerce.radar.parser;

import com.commerce.radar.parser.model.IssueKind;
import com.commerce.radar.parser.model.LogLine;
import com.commerce.radar.parser.model.ParsedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Streaming multi-line parser for Hybris / Spring console logs.
 *
 * <p>Rules (stable):
 * <ul>
 *   <li>A line containing {@code ERROR} / {@code WARN} / {@code FATAL} as a log header starts an event.</li>
 *   <li>Following stack lines ({@code at }, {@code Caused by:}, {@code ... N more}) belong to that event.</li>
 *   <li>Any other normal log header closes the previous event.</li>
 *   <li>Non-header lines while an event is open (exception type lines) are appended.</li>
 * </ul>
 */
public final class HybrisLogParser {

    private final String customPackagePrefix;
    private EventBuilder current;

    public HybrisLogParser(String customPackagePrefix) {
        this.customPackagePrefix = customPackagePrefix == null || customPackagePrefix.isBlank()
                ? "com.yourcompany"
                : customPackagePrefix.trim();
    }

    public HybrisLogParser() {
        this("com.yourcompany");
    }

    /**
     * Feed one line. Returns a closed event when the previous event is finished.
     */
    public Optional<ParsedEvent> accept(String rawLine) {
        String line = LogLine.unwrapWrapper(rawLine == null ? "" : rawLine.stripTrailing());
        Optional<LogLine> header = LogLine.parseHeader(line);

        if (header.isPresent()) {
            LogLine h = header.get();
            Optional<ParsedEvent> closed = closeCurrent();
            if (h.isErrorOrWarnLevel()) {
                current = EventBuilder.start(h);
            }
            return closed;
        }

        if (current != null) {
            current.append(line);
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Flush a still-open event (EOF / log rotation).
     */
    public Optional<ParsedEvent> flush() {
        return closeCurrent();
    }

    public boolean hasOpenEvent() {
        return current != null;
    }

    public List<ParsedEvent> parseAll(Iterable<String> lines) {
        List<ParsedEvent> events = new ArrayList<>();
        for (String line : lines) {
            accept(line).ifPresent(events::add);
        }
        flush().ifPresent(events::add);
        return events;
    }

    private Optional<ParsedEvent> closeCurrent() {
        if (current == null) {
            return Optional.empty();
        }
        ParsedEvent event = current.build(customPackagePrefix);
        current = null;
        return Optional.of(event);
    }

    private static final class EventBuilder {
        private final LogLine header;
        private final StringBuilder raw = new StringBuilder();
        private final StringBuilder stack = new StringBuilder();

        private EventBuilder(LogLine header) {
            this.header = header;
            raw.append(header.raw());
        }

        static EventBuilder start(LogLine header) {
            return new EventBuilder(header);
        }

        void append(String line) {
            raw.append('\n').append(line);
            if (!line.isBlank() || !stack.isEmpty()) {
                if (!stack.isEmpty()) {
                    stack.append('\n');
                }
                stack.append(line);
            }
        }

        ParsedEvent build(String customPrefix) {
            String rawText = raw.toString();
            String stackText = stack.toString();
            String exceptionType = LogLine.extractExceptionType(stackText);
            if (exceptionType.isBlank()) {
                exceptionType = LogLine.extractExceptionType(header.message() + "\n" + rawText);
            }
            StackFingerprint.Result fp = new StackFingerprint(customPrefix).compute(exceptionType, stackText);
            Map<String, String> ids = BusinessIdExtractor.extract(rawText);
            IssueKind kind = IssueClassifier.classify(
                    header.logger(), header.thread(), header.message(), exceptionType, rawText);
            return new ParsedEvent(
                    header.timestamp(),
                    header.level(),
                    header.thread(),
                    header.logger(),
                    header.message(),
                    exceptionType,
                    stackText,
                    rawText,
                    fp.fingerprint(),
                    fp.hasCustomFrame(),
                    kind,
                    ids
            );
        }
    }
}
