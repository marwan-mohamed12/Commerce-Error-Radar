package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import com.commerce.radar.config.RadarProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorNotifyServiceTest {

    @Mock
    private RadarRepository repository;
    @Mock
    private LiveEventHub hub;

    private final RecordingToaster toaster = new RecordingToaster();
    private ErrorNotifyService service;

    @BeforeEach
    void setUp() {
        RadarProperties properties = new RadarProperties();
        properties.setNotifyOnError(true);
        service = new ErrorNotifyService(properties, repository, hub, toaster);
    }

    @Test
    void sseAlways_toastOnlyWhileTabHidden() {
        service.setTabHidden(false);
        assertTrue(service.onEvent(issue(false), event("ERROR")).isPresent());
        verify(hub).publishNotify(org.mockito.ArgumentMatchers.any());
        assertTrue(toaster.shown.isEmpty());

        service.setTabHidden(true);
        service.onEvent(issue(false), event("ERROR"));
        assertEquals(1, toaster.shown.size());
        assertEquals("Radar · ERROR · OCC", toaster.shown.getFirst().toastTitle());
    }

    @Test
    void persistsToggle() {
        service.setEnabled(false);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(repository).putSetting(
                org.mockito.ArgumentMatchers.eq(ErrorNotifyService.SETTING_ENABLED),
                value.capture()
        );
        assertEquals("false", value.getValue());
        assertTrue(service.onEvent(issue(false), event("ERROR")).isEmpty());
        verify(hub, never()).publishNotify(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enablingFiresConfirmationToastEvenWhenTabFocused() {
        service.setTabHidden(false);
        service.setEnabled(true);
        assertEquals(1, toaster.shown.size());
        assertEquals("Notifications on", toaster.shown.getFirst().title());
    }

    @Test
    void storedSettingOverridesProperty() {
        when(repository.findSetting(ErrorNotifyService.SETTING_ENABLED)).thenReturn(Optional.of("false"));
        RadarProperties properties = new RadarProperties();
        properties.setNotifyOnError(true);
        ErrorNotifyService loaded = new ErrorNotifyService(properties, repository, hub, toaster);
        loaded.loadStoredPreference();
        assertTrue(loaded.onEvent(issue(false), event("ERROR")).isEmpty());
    }

    private static StoredIssue issue(boolean muted) {
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        return new StoredIssue("fp", "OCC NPE", "ERROR", "OCC", 1, now, now, true, muted, "msg", Map.of());
    }

    private static StoredEvent event(String level) {
        return new StoredEvent(
                1L, 1L, Instant.parse("2026-08-14T12:00:01Z"),
                level, "log", "t", "boom", "NPE", "fp", "raw", "ctx", "OCC", true, Map.of()
        );
    }

    private static final class RecordingToaster implements ErrorToaster {
        private final List<ErrorNotification> shown = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void show(ErrorNotification notification) {
            shown.add(notification);
        }
    }
}
