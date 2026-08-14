package com.commerce.radar.application;

import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.parser.model.IssueKind;
import com.commerce.radar.parser.model.ParsedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseFilterTest {

    @Test
    void dropsConfiguredSubstrings() {
        RadarProperties properties = new RadarProperties();
        properties.setIgnorePatterns(List.of("Solr ping", "session replication"));
        NoiseFilter filter = new NoiseFilter(properties);

        assertTrue(filter.ignored(event("Solr ping failed for endpoint"), ""));
        assertTrue(filter.ignored(event("session replication failed"), "unrelated context"));
        assertFalse(filter.ignored(event("Failed to add product to cart"), "Solr ping failed earlier in the buffer"));
    }

    private static ParsedEvent event(String message) {
        return new ParsedEvent(
                null, "WARN", "t", "logger", message, "", "", message,
                "x", false, IssueKind.OTHER, Map.of()
        );
    }
}
