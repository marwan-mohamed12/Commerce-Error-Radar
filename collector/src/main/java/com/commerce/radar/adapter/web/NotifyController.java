package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.NotifyEnabledRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.NotifyPresenceRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.NotifySettingsResponse;
import com.commerce.radar.application.ErrorNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify")
@Tag(name = "Notify", description = "ERROR toast toggle and tab presence")
public class NotifyController {

    private final ErrorNotifyService notify;

    public NotifyController(ErrorNotifyService notify) {
        this.notify = notify;
    }

    @GetMapping
    @Operation(summary = "Get notify settings")
    public NotifySettingsResponse get() {
        return snapshot();
    }

    @PostMapping
    @Operation(summary = "Turn the bell on or off", description = "Persisted in SQLite. Enabling fires a confirmation toast on Windows.")
    public NotifySettingsResponse setEnabled(@RequestBody NotifyEnabledRequest request) {
        notify.setEnabled(request != null && request.enabled());
        return snapshot();
    }

    @PostMapping("/presence")
    @Operation(summary = "Report tab presence", description = "The UI posts this. hidden=true lets the collector toast on the next ERROR.")
    public NotifySettingsResponse presence(@RequestBody NotifyPresenceRequest request) {
        notify.setTabHidden(request == null || request.hidden());
        return snapshot();
    }

    private NotifySettingsResponse snapshot() {
        return new NotifySettingsResponse(
                notify.isEnabled(),
                notify.isTabHidden(),
                notify.windowsToastAvailable()
        );
    }
}
