package com.commerce.radar.api;

import com.commerce.radar.api.dto.IssueDtos.OpenLogRequest;
import com.commerce.radar.api.dto.IssueDtos.RunStatusResponse;
import com.commerce.radar.config.RadarProperties;
import com.commerce.radar.tail.ConsoleLogTailer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final ConsoleLogTailer tailer;
    private final RadarProperties properties;

    public RunController(ConsoleLogTailer tailer, RadarProperties properties) {
        this.tailer = tailer;
        this.properties = properties;
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
