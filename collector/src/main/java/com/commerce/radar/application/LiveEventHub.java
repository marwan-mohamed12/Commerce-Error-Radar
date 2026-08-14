package com.commerce.radar.application;

import com.commerce.radar.adapter.persistence.StoredEvent;
import com.commerce.radar.adapter.persistence.StoredIssue;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LiveEventHub {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("hello")
                    .data(Map.of("ok", true, "ts", Instant.now().toString()), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(StoredIssue issue, StoredEvent event) {
        Map<String, Object> payload = Map.of(
                "type", "issue",
                "issue", issue,
                "event", event
        );
        broadcast("issue", payload);
    }

    public void publishNotify(ErrorNotification notification) {
        broadcast("notify", notification);
    }

    public void publishStatus(Object status) {
        broadcast("status", status);
    }

    private void broadcast(String name, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }
}
