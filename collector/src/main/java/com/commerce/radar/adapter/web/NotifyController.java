package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.NotifyEnabledRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.NotifyPresenceRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.NotifySettingsResponse;
import com.commerce.radar.application.ErrorNotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    private final ErrorNotifyService notify;

    public NotifyController(ErrorNotifyService notify) {
        this.notify = notify;
    }

    @GetMapping
    public NotifySettingsResponse get() {
        return snapshot();
    }

    @PostMapping
    public NotifySettingsResponse setEnabled(@RequestBody NotifyEnabledRequest request) {
        notify.setEnabled(request != null && request.enabled());
        return snapshot();
    }

    @PostMapping("/presence")
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
