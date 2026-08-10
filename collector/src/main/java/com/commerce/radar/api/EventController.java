package com.commerce.radar.api;

import com.commerce.radar.api.dto.IssueDtos.EventResponse;
import com.commerce.radar.store.RadarRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final RadarRepository repository;

    public EventController(RadarRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<EventResponse> search(
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return repository.searchEvents(level, q, capped)
                .stream()
                .map(EventResponse::from)
                .toList();
    }
}
