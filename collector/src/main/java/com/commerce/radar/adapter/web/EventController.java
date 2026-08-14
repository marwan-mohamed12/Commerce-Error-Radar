package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.EventResponse;
import com.commerce.radar.adapter.persistence.RadarRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Flat search across stored events")
public class EventController {

    private final RadarRepository repository;

    public EventController(RadarRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Search events")
    public List<EventResponse> search(
            @Parameter(description = "ERROR, WARN, or omit for all")
            @RequestParam(name = "level", required = false) String level,
            @Parameter(description = "Search message, logger, or stack text")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "1–500. Default 100.")
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return repository.searchEvents(level, q, capped)
                .stream()
                .map(EventResponse::from)
                .toList();
    }
}
