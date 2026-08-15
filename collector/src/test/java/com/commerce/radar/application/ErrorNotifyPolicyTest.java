package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorNotifyPolicyTest {

    @Test
    void onlyEnabledErrorOrFatal() {
        assertFalse(ErrorNotifyPolicy.shouldNotify(false, issue("ERROR", false), event("ERROR")));
        assertFalse(ErrorNotifyPolicy.shouldNotify(true, issue("WARN", false), event("WARN")));
        assertTrue(ErrorNotifyPolicy.shouldNotify(true, issue("ERROR", false), event("ERROR")));
        assertTrue(ErrorNotifyPolicy.shouldNotify(true, issue("FATAL", false), event("FATAL")));
    }

    @Test
    void mutedFingerprintIsQuiet() {
        assertFalse(ErrorNotifyPolicy.shouldNotify(true, issue("ERROR", true), event("ERROR")));
    }

    @Test
    void eventLevelWinsOverIssueLevel() {
        assertTrue(ErrorNotifyPolicy.shouldNotify(true, issue("WARN", false), event("ERROR")));
        assertFalse(ErrorNotifyPolicy.shouldNotify(true, issue("ERROR", false), event("WARN")));
    }

    @Test
    void payloadUsesEventMessage() {
        ErrorNotification ping = ErrorNotification.from(issue("ERROR", false), event("ERROR"));
        assertEquals("NullPointerException at CheckoutFacade", ping.message());
        assertEquals("Radar · ERROR · OCC", ping.toastTitle());
        assertEquals("OCC", ping.kind());
    }

    @Test
    void confirmationPingIsAToastNotAnIssue() {
        ErrorNotification ping = ErrorNotification.confirmation();
        assertEquals("Notifications on", ping.title());
        assertTrue(ping.message().contains("unfocused"));
        assertEquals("", ping.fingerprint());
    }

    private static StoredIssue issue(String level, boolean muted) {
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        return new StoredIssue(
                "NullPointerException@com.shop.CheckoutFacade",
                "OCC NullPointerException",
                level,
                "OCC",
                4,
                now,
                now,
                true,
                muted,
                "older message",
                Map.of(),
                ""
        );
    }

    private static StoredEvent event(String level) {
        return new StoredEvent(
                1L,
                1L,
                Instant.parse("2026-08-14T12:00:01Z"),
                level,
                "de.hybris",
                "hybrisHTTP1",
                "NullPointerException at CheckoutFacade",
                "NullPointerException",
                "NullPointerException@com.shop.CheckoutFacade",
                "raw",
                "ctx",
                "OCC",
                true,
                Map.of()
        );
    }
}
