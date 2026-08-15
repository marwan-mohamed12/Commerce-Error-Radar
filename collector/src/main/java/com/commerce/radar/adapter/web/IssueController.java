package com.commerce.radar.adapter.web;

import com.commerce.radar.adapter.web.dto.IssueDtos.EventResponse;
import com.commerce.radar.adapter.web.dto.IssueDtos.IssueDetailResponse;
import com.commerce.radar.adapter.web.dto.IssueDtos.IssueResponse;
import com.commerce.radar.adapter.web.dto.IssueDtos.MuteRequest;
import com.commerce.radar.adapter.persistence.RadarRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.commerce.radar.adapter.persistence.StoredIssue;
import com.commerce.radar.adapter.tail.ConsoleLogTailer;
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
@Tag(name = "Issues", description = "Grouped ERROR/WARN for live logs or one session. Fingerprints go in a query param.")
public class IssueController {

    private final RadarRepository repository;
    private final ConsoleLogTailer tailer;

    public IssueController(RadarRepository repository, ConsoleLogTailer tailer) {
        this.repository = repository;
        this.tailer = tailer;
    }

    @GetMapping
    @Operation(summary = "List issues", description = "Empty runId uses the current session.")
    public List<IssueResponse> list(
            @Parameter(description = "ERROR, WARN, or omit for all")
            @RequestParam(name = "level", required = false) String level,
            @Parameter(description = "OCC, CRONJOB, IMPEX, FLEXIBLE_SEARCH, SOLR, INTERCEPTOR, MODEL_SAVE, INITIALIZE, UPDATE, ANT, TOMCAT, OTHER")
            @RequestParam(name = "kind", required = false) String kind,
            @Parameter(description = "Search title, message, or class")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "order, product, user, cronjob, impex, catalogVersion")
            @RequestParam(name = "bizKey", required = false) String bizKey,
            @RequestParam(name = "bizValue", required = false) String bizValue,
            @Parameter(description = "Session id. Omit for the live sources (All or logKind).")
            @RequestParam(name = "runId", required = false) Long runId,
            @Parameter(description = "ALL, CONSOLE, WRAPPER, ANT, CATALINA, LOCALHOST")
            @RequestParam(name = "logKind", required = false) String logKind,
            @RequestParam(name = "includeMuted", defaultValue = "false") boolean includeMuted
    ) {
        List<Long> sessions = resolveRunIds(runId, logKind);
        if (sessions.isEmpty()) {
            return List.of();
        }
        return repository.listIssuesForRuns(sessions, level, kind, q, bizKey, bizValue, includeMuted)
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    @GetMapping("/one")
    @Operation(summary = "Get one issue", description = "Must use a query param — fingerprints contain @.")
    @ApiResponse(responseCode = "200", description = "Issue and recent events")
    @ApiResponse(responseCode = "404", description = "Unknown fingerprint in this session", content = @Content)
    public ResponseEntity<IssueDetailResponse> detail(
            @Parameter(required = true, example = "NullPointerException@com.yourcompany.facades.impl.DefaultCartFacade.addToCart")
            @RequestParam("fingerprint") String fingerprint,
            @RequestParam(name = "runId", required = false) Long runId,
            @RequestParam(name = "logKind", required = false) String logKind
    ) {
        String decoded = fingerprint == null ? "" : URLDecoder.decode(fingerprint, StandardCharsets.UTF_8);
        List<Long> sessions = resolveRunIds(runId, logKind);
        StoredIssue issue = !sessions.isEmpty()
                ? repository.findIssueInRuns(decoded, sessions).orElse(null)
                : repository.findIssue(decoded).orElse(null);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        List<EventResponse> events = repository.listEventsForFingerprint(decoded, sessions, 25)
                .stream()
                .map(EventResponse::from)
                .toList();
        return ResponseEntity.ok(new IssueDetailResponse(IssueResponse.from(issue), events));
    }

    @PostMapping("/mute")
    @Operation(summary = "Mute or unmute", description = "Mute is per fingerprint and global.")
    @ApiResponse(responseCode = "200", description = "Updated issue")
    @ApiResponse(responseCode = "404", description = "Unknown fingerprint", content = @Content)
    public ResponseEntity<IssueResponse> mute(
            @Parameter(required = true, example = "NullPointerException@com.yourcompany.facades.impl.DefaultCartFacade.addToCart")
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

    private List<Long> resolveRunIds(Long runId, String logKind) {
        if (runId != null && runId > 0) {
            return List.of(runId);
        }
        return tailer.activeRunIds(logKind);
    }
}
