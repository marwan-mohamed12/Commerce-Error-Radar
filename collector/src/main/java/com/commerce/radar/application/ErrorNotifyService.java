package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import com.commerce.radar.config.RadarProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Optional ERROR notify: persist the toggle, tell the UI via SSE, toast on Windows
 * only while the dashboard tab reports it is in the background.
 */
@Service
@DependsOn("schemaInitializer")
public class ErrorNotifyService {

    public static final String SETTING_ENABLED = "notify.enabled";

    private static final Logger log = LoggerFactory.getLogger(ErrorNotifyService.class);

    private final RadarProperties properties;
    private final RadarRepository repository;
    private final LiveEventHub hub;
    private final ErrorToaster toaster;

    private volatile boolean enabled;
    private volatile boolean tabHidden = true;

    public ErrorNotifyService(
            RadarProperties properties,
            RadarRepository repository,
            LiveEventHub hub,
            ErrorToaster toaster
    ) {
        this.properties = properties;
        this.repository = repository;
        this.hub = hub;
        this.toaster = toaster;
        this.enabled = properties.isNotifyOnError();
    }

    @PostConstruct
    void loadStoredPreference() {
        repository.findSetting(SETTING_ENABLED).ifPresent(value ->
                enabled = "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTabHidden() {
        return tabHidden;
    }

    public boolean windowsToastAvailable() {
        return toaster.available();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        repository.putSetting(SETTING_ENABLED, enabled ? "true" : "false");
        log.info("ERROR notifications {}", enabled ? "on" : "off");
    }

    public void setTabHidden(boolean hidden) {
        this.tabHidden = hidden;
    }

    public Optional<ErrorNotification> onEvent(StoredIssue issue, StoredEvent event) {
        if (!ErrorNotifyPolicy.shouldNotify(enabled, issue, event)) {
            return Optional.empty();
        }
        ErrorNotification notification = ErrorNotification.from(issue, event);
        hub.publishNotify(notification);
        if (tabHidden) {
            toaster.show(notification);
        }
        return Optional.of(notification);
    }
}
