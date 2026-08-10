package com.commerce.radar.api;

import com.commerce.radar.api.dto.IssueDtos.EventResponse;
import com.commerce.radar.api.dto.IssueDtos.IssueDetailResponse;
import com.commerce.radar.api.dto.IssueDtos.IssueResponse;
import com.commerce.radar.api.dto.IssueDtos.MuteRequest;
import com.commerce.radar.store.RadarRepository;
import com.commerce.radar.store.StoredIssue;
import com.commerce.radar.tail.ConsoleLogTailer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final RadarRepository repository;
    private final ConsoleLogTailer tailer;

    public IssueController(RadarRepository repository, ConsoleLogTailer tailer) {
        this.repository = repository;
        this.tailer = tailer;
    }

    @GetMapping
    public List<IssueResponse> list(
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "kind", required = false) String kind,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "runId", required = false) Long runId,
            @RequestParam(name = "includeMuted", defaultValue = "false") boolean includeMuted
    ) {
        long session = resolveRunId(runId);
        if (session <= 0) {
            return List.of();
        }
        return repository.listIssues(session, level, kind, q, includeMuted)
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    @GetMapping("/one")
    public ResponseEntity<IssueDetailResponse> detail(
            @RequestParam("fingerprint") String fingerprint,
            @RequestParam(name = "runId", required = false) Long runId
    ) {
        String decoded = fingerprint == null ? "" : URLDecoder.decode(fingerprint, StandardCharsets.UTF_8);
        long session = resolveRunId(runId);
        StoredIssue issue = session > 0
                ? repository.findIssueInRun(decoded, session).orElse(null)
                : repository.findIssue(decoded).orElse(null);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        List<EventResponse> events = repository.listEventsForFingerprint(decoded, session > 0 ? session : null, 25)
                .stream()
                .map(EventResponse::from)
                .toList();
        return ResponseEntity.ok(new IssueDetailResponse(IssueResponse.from(issue), events));
    }

    @PostMapping("/mute")
    public ResponseEntity<IssueResponse> mute(
            @RequestParam("fingerprint") String fingerprint,
            @RequestBody(required = false) MuteRequest request
    ) {
        String decoded = fingerprint == null ? "" : URLDecoder.decode(fingerprint, StandardCharsets.UTF_8);
        boolean muted = request == null || request.muted();
        repository.setMuted(decoded, muted);
        return repository.findIssue(decoded)
                .map(IssueResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private long resolveRunId(Long runId) {
        if (runId != null && runId > 0) {
            return runId;
        }
        return tailer.status().getRunId();
    }
}
