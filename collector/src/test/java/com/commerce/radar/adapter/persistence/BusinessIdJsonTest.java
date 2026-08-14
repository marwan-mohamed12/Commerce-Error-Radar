package com.commerce.radar.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessIdJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void needleIsInsideJacksonObject() {
        String json = JsonMaps.write(mapper, Map.of("order", "00001234", "impex", "products-delta.impex"));
        String order = BusinessIdJson.containsNeedle(mapper, "order", "00001234");
        String impex = BusinessIdJson.containsNeedle(mapper, "impex", "products-delta.impex");
        assertNotNull(order);
        assertNotNull(impex);
        assertTrue(json.contains(order));
        assertTrue(json.contains(impex));
        assertFalse(json.contains(BusinessIdJson.containsNeedle(mapper, "order", "99999")));
    }

    @Test
    void blankIsIgnored() {
        assertNull(BusinessIdJson.containsNeedle(mapper, "order", " "));
        assertNull(BusinessIdJson.containsNeedle(mapper, "", "0001"));
    }
}
