package com.commerce.radar.adapter.web;

import com.commerce.radar.application.LiveEventHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@Tag(name = "Stream", description = "Server-sent events for the live inbox")
public class StreamController {

    private final LiveEventHub hub;

    public StreamController(LiveEventHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE stream",
            description = "Long-lived connection. Event names: hello, issue, status, notify. Swagger Try it out will sit open until you cancel."
    )
    public SseEmitter stream() {
        return hub.subscribe();
    }
}
