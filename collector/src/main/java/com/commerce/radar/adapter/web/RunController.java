package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.OpenLogRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.RunStatusResponse;
import com.commerce.radar.adapter.web.dto.IssueDtos.RunSummaryResponse;
import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.tail.ConsoleLogTailer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final ConsoleLogTailer tailer;
    private final RadarProperties properties;
    private final RadarRepository repository;

    public RunController(ConsoleLogTailer tailer, RadarProperties properties, RadarRepository repository) {
        this.tailer = tailer;
        this.properties = properties;
        this.repository = repository;
    }

    @GetMapping
    public List<RunSummaryResponse> list() {
        long currentId = tailer.status().getRunId();
        return repository.listRuns().stream()
                .map(run -> RunSummaryResponse.from(run, currentId))
                .toList();
    }

    @GetMapping("/current")
    public RunStatusResponse current() {
        return RunStatusResponse.from(tailer.status(), properties.getCustomPackagePrefix());
    }

    @PostMapping("/open")
    public ResponseEntity<RunStatusResponse> open(@Valid @RequestBody OpenLogRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        tailer.openFile(Path.of(request.path()), !request.replay());
        return ResponseEntity.ok(RunStatusResponse.from(tailer.status(), properties.getCustomPackagePrefix()));
    }
}
