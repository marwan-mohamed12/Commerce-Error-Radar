package com.commerce.radar.api;

import com.commerce.radar.api.dto.IssueDtos.EventResponse;
import com.commerce.radar.api.dto.IssueDtos.IssueDetailResponse;
import com.commerce.radar.api.dto.IssueDtos.IssueResponse;
import com.commerce.radar.api.dto.IssueDtos.MuteRequest;
import com.commerce.radar.store.RadarRepository;
import com.commerce.radar.store.StoredIssue;
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

    public IssueController(RadarRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<IssueResponse> list(
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "kind", required = false) String kind,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "mineOnly", defaultValue = "false") boolean mineOnly,
            @RequestParam(name = "includeMuted", defaultValue = "false") boolean includeMuted
    ) {
        return repository.listIssues(level, kind, q, mineOnly, includeMuted)
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    @GetMapping("/one")
    public ResponseEntity<IssueDetailResponse> detail(@RequestParam("fingerprint") String fingerprint) {
        String decoded = fingerprint == null ? "" : URLDecoder.decode(fingerprint, StandardCharsets.UTF_8);
        StoredIssue issue = repository.findIssue(decoded).orElse(null);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        List<EventResponse> events = repository.listEventsForFingerprint(decoded, 25)
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
}
