package com.commerce.radar.ingest;

import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.parser.ParsedEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class NoiseFilter {

    private final RadarProperties properties;

    public NoiseFilter(RadarProperties properties) {
        this.properties = properties;
    }

    public boolean ignored(ParsedEvent event, String context) {
        if (event == null) {
            return true;
        }
        String blob = (event.rawText() + "\n" + event.message() + "\n" + event.logger())
                .toLowerCase(Locale.ROOT);
        for (String pattern : properties.getIgnorePatterns()) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (blob.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
