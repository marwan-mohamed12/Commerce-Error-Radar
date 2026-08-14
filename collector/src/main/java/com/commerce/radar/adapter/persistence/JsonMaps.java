package com.commerce.radar.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonMaps {

    private static final TypeReference<LinkedHashMap<String, String>> MAP = new TypeReference<>() {
    };

    private JsonMaps() {
    }

    public static String write(ObjectMapper mapper, Map<String, String> ids) {
        try {
            return mapper.writeValueAsString(ids == null ? Map.of() : ids);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static Map<String, String> read(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, String> value = mapper.readValue(json, MAP);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
