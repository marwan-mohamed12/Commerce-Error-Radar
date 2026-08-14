package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.OpenLogRequest;
import com.commerce.radar.adapter.web.dto.IssueDtos.RunStatusResponse;
import com.commerce.radar.adapter.web.dto.IssueDtos.RunSummaryResponse;
import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.adapter.persistence.RadarRepository;
import com.commerce.radar.adapter.tail.ConsoleLogTailer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Runs", description = "One console-*.log file is one session")
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
    @Operation(summary = "List sessions", description = "Every console file Radar has opened.")
    public List<RunSummaryResponse> list() {
        long currentId = tailer.status().getRunId();
        return repository.listRuns().stream()
                .map(run -> RunSummaryResponse.from(run, currentId))
                .toList();
    }

    @GetMapping("/current")
    @Operation(summary = "Current run", description = "Tail status for the file Radar is reading now.")
    public RunStatusResponse current() {
        return RunStatusResponse.from(tailer.status(), properties.getCustomPackagePrefix());
    }

    @PostMapping("/open")
    @Operation(summary = "Open a log file")
    @ApiResponse(responseCode = "200", description = "Tailer switched to this file")
    @ApiResponse(responseCode = "400", description = "path is missing", content = @Content)
    public ResponseEntity<RunStatusResponse> open(@Valid @RequestBody OpenLogRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        tailer.openFile(Path.of(request.path()), !request.replay());
        return ResponseEntity.ok(RunStatusResponse.from(tailer.status(), properties.getCustomPackagePrefix()));
    }
}
