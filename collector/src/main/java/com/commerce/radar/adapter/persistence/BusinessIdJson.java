package com.commerce.radar.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Builds the exact JSON fragment Jackson writes for one business id,
 * so SQLite {@code instr(json, needle)} matches a key/value pair.
 */
public final class BusinessIdJson {

    private BusinessIdJson() {
    }

    /**
     * {@code "order":"00001234"} (no braces). {@code null} when key or value is blank.
     */
    public static String containsNeedle(ObjectMapper mapper, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank() || mapper == null) {
            return null;
        }
        String json = JsonMaps.write(mapper, Map.of(key.trim(), value.trim()));
        if (json.length() >= 2 && json.charAt(0) == '{' && json.charAt(json.length() - 1) == '}') {
            return json.substring(1, json.length() - 1);
        }
        return json;
    }
}
