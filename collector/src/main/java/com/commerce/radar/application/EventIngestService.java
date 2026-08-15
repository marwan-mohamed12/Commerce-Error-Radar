package com.commerce.radar.application;

import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.parser.BusinessIdExtractor;
import com.commerce.radar.parser.IssueClassifier;
import com.commerce.radar.parser.model.ParsedEvent;
import com.commerce.radar.parser.StackFingerprint;
import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EventIngestService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestService.class);

    private final RadarRepository repository;
    private final NoiseFilter noiseFilter;
    private final LiveEventHub hub;
    private final ErrorNotifyService notify;
    private final RadarProperties properties;

    public EventIngestService(
            RadarRepository repository,
            NoiseFilter noiseFilter,
            LiveEventHub hub,
            ErrorNotifyService notify,
            RadarProperties properties
    ) {
        this.repository = repository;
        this.noiseFilter = noiseFilter;
        this.hub = hub;
        this.notify = notify;
        this.properties = properties;
    }

    public synchronized StoredIssue ingest(long runId, ParsedEvent parsed, String context) {
        if (parsed == null) {
            return null;
        }
        if (noiseFilter.ignored(parsed, context)) {
            log.debug("Ignored noise event: {}", parsed.message());
            return null;
        }
        Instant ts = parsed.timestamp() == null ? Instant.now() : parsed.timestamp();
        Map<String, String> ids = new LinkedHashMap<>(parsed.businessIds());
        BusinessIdExtractor.extract(parsed.rawText(), context).forEach(ids::putIfAbsent);

        StackFingerprint.Result fp = new StackFingerprint(properties.getCustomPackagePrefix())
                .compute(parsed.exceptionType(), parsed.stackText());
        String title = IssueClassifier.title(
                parsed.kind(), parsed.exceptionType(), parsed.message(), parsed.logger(), fp, ids);

        StoredEvent stored = repository.insertEvent(new StoredEvent(
                0L,
                runId,
                ts,
                parsed.level(),
                parsed.logger(),
                parsed.thread(),
                parsed.message(),
                parsed.exceptionType(),
                parsed.fingerprint(),
                parsed.rawText(),
                context == null ? "" : context,
                parsed.kind().name(),
                parsed.hasCustomFrame(),
                ids
        ));
        StoredIssue issue = repository.upsertIssue(new StoredIssue(
                parsed.fingerprint(),
                title,
                parsed.level(),
                parsed.kind().name(),
                1,
                ts,
                ts,
                parsed.hasCustomFrame(),
                false,
                parsed.message(),
                ids,
                ""
        ));
        hub.publish(issue, stored);
        notify.onEvent(issue, stored);
        return issue;
    }
}
