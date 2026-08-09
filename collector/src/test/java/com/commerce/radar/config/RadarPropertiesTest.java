package com.commerce.radar.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarPropertiesTest {

    @Test
    void firstNonBlankSkipsEmptyAndWhitespace() {
        assertEquals("D:/hybris", RadarProperties.firstNonBlank("", "  ", "D:/hybris"));
        assertNull(RadarProperties.firstNonBlank(null, "", "   "));
    }

    @Test
    void blankPropertyIsNotAHybrisHome() {
        RadarProperties properties = new RadarProperties();
        properties.setHybrisHome("");
        // Do not treat an empty CLI/property as configured. Env may still fill it.
        if (System.getenv("HYBRIS_HOME") == null || System.getenv("HYBRIS_HOME").isBlank()) {
            assertFalse(properties.hasHybrisHome());
            assertNull(properties.resolvedHybrisHome());
        }
    }

    @Test
    void configuredPathWinsOverEmptyPlaceholders() {
        RadarProperties properties = new RadarProperties();
        properties.setHybrisHome("D:/dccp-digitalcommerce-customerportal/core-customize/hybris");
        assertTrue(properties.hasHybrisHome());
        assertEquals(
                "D:\\dccp-digitalcommerce-customerportal\\core-customize\\hybris"
                        .replace('\\', '/'),
                properties.resolvedHybrisHome().toString().replace('\\', '/')
        );
    }

    @Test
    void trimsWhitespace() {
        RadarProperties properties = new RadarProperties();
        properties.setHybrisHome("  D:/hybris  ");
        assertEquals("D:/hybris", properties.getHybrisHome());
    }
}
