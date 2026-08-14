package com.commerce.radar.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class RadarRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RadarRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public StoredRun insertRun(String hybrisHome, String logPath, String mode) {
        Instant now = Instant.now();
        String path = LogPaths.normalize(logPath);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO runs (hybris_home, log_path, started_at, mode) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, hybrisHome);
            ps.setString(2, path);
            ps.setString(3, now.toString());
            ps.setString(4, mode);
            return ps;
        }, keys);
        Number id = keys.getKey();
        long runId = id == null ? 0L : id.longValue();
        return new StoredRun(runId, hybrisHome, path, now, null, mode);
    }

    /**
     * One console file is one session. Reopening the same path resumes the existing run
     * and folds any duplicate run rows for that file into it.
     */
    public StoredRun findOrOpenRun(String hybrisHome, Path logFile, String mode) {
        consolidateRunsByLogPath();
        String path = LogPaths.normalize(logFile);
        List<StoredRun> matches = listAllRuns().stream()
                .filter(run -> LogPaths.normalize(run.logPath()).equals(path))
                .sorted(Comparator.comparingLong(StoredRun::id))
                .toList();
        if (matches.isEmpty()) {
            return insertRun(hybrisHome, path, mode);
        }
        StoredRun keep = matches.getFirst();
        jdbc.update(
                "UPDATE runs SET ended_at = NULL, log_path = ?, hybris_home = ?, mode = ? WHERE id = ?",
                path,
                hybrisHome,
                mode,
                keep.id()
        );
        return findRun(keep.id()).orElseThrow();
    }

    public void consolidateRunsByLogPath() {
        Map<String, List<StoredRun>> byPath = new LinkedHashMap<>();
        for (StoredRun run : listAllRuns()) {
            byPath.computeIfAbsent(LogPaths.normalize(run.logPath()), key -> new ArrayList<>()).add(run);
        }
        for (List<StoredRun> group : byPath.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparingLong(StoredRun::id));
            long keepId = group.getFirst().id();
            for (StoredRun extra : group.subList(1, group.size())) {
                jdbc.update("UPDATE events SET run_id = ? WHERE run_id = ?", keepId, extra.id());
                jdbc.update("DELETE FROM runs WHERE id = ?", extra.id());
            }
        }
    }

    private List<StoredRun> listAllRuns() {
        return jdbc.query("SELECT * FROM runs ORDER BY id", runMapper());
    }

    public void updateRunLogPath(long runId, String logPath) {
        jdbc.update("UPDATE runs SET log_path = ? WHERE id = ?", logPath, runId);
    }

    public void endRun(long runId) {
        jdbc.update("UPDATE runs SET ended_at = ? WHERE id = ? AND ended_at IS NULL", Instant.now().toString(), runId);
    }

    public Optional<StoredRun> findRun(long id) {
        List<StoredRun> rows = jdbc.query("SELECT * FROM runs WHERE id = ?", runMapper(), id);
        return rows.stream().findFirst();
    }

    public Optional<StoredRun> findLatestRun() {
        List<StoredRun> rows = jdbc.query("SELECT * FROM runs ORDER BY id DESC LIMIT 1", runMapper());
        return rows.stream().findFirst();
    }

    public List<RunSummary> listRuns() {
        consolidateRunsByLogPath();
        return jdbc.query(
                """
                SELECT r.*,
                    (SELECT COUNT(*) FROM events e WHERE e.run_id = r.id) AS event_count,
                    (SELECT COUNT(DISTINCT e.fingerprint) FROM events e WHERE e.run_id = r.id) AS issue_count
                FROM runs r
                ORDER BY r.id DESC
                """,
                (rs, rowNum) -> new RunSummary(
                        rs.getLong("id"),
                        rs.getString("hybris_home"),
                        rs.getString("log_path"),
                        parseInstant(rs.getString("started_at")),
                        parseInstant(rs.getString("ended_at")),
                        rs.getString("mode"),
                        rs.getLong("event_count"),
                        rs.getLong("issue_count")
                )
        );
    }

    /**
     * Drop leftover DEMO replay rows so a LIVE session does not keep showing sample issues.
     *
     * @return number of issues removed
     */
    public int deleteDemoData() {
        jdbc.update("DELETE FROM events WHERE run_id IN (SELECT id FROM runs WHERE upper(ifnull(mode, '')) = 'DEMO')");
        int issues = jdbc.update(
                "DELETE FROM issues WHERE fingerprint NOT IN (SELECT DISTINCT fingerprint FROM events)"
        );
        jdbc.update("DELETE FROM runs WHERE upper(ifnull(mode, '')) = 'DEMO'");
        return issues;
    }

    public StoredEvent insertEvent(StoredEvent event) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO events (
                        run_id, ts, level, logger, thread, message, exception, fingerprint,
                        raw_text, context_text, kind, has_custom_frame, business_ids_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, event.runId());
            ps.setString(2, event.ts().toString());
            ps.setString(3, event.level());
            ps.setString(4, event.logger());
            ps.setString(5, event.thread());
            ps.setString(6, event.message());
            ps.setString(7, event.exception());
            ps.setString(8, event.fingerprint());
            ps.setString(9, event.rawText());
            ps.setString(10, event.contextText());
            ps.setString(11, event.kind());
            ps.setInt(12, event.hasCustomFrame() ? 1 : 0);
            ps.setString(13, JsonMaps.write(mapper, event.businessIds()));
            return ps;
        }, keys);
        Number id = keys.getKey();
        long eventId = id == null ? 0L : id.longValue();
        return new StoredEvent(
                eventId, event.runId(), event.ts(), event.level(), event.logger(), event.thread(),
                event.message(), event.exception(), event.fingerprint(), event.rawText(), event.contextText(),
                event.kind(), event.hasCustomFrame(), event.businessIds()
        );
    }

    public StoredIssue upsertIssue(StoredIssue incoming) {
        StoredIssue existing = findIssue(incoming.fingerprint()).orElse(null);
        if (existing == null) {
            jdbc.update(
                    """
                    INSERT INTO issues (
                        fingerprint, title, level, kind, count, first_seen, last_seen,
                        has_custom_frame, muted, last_message, last_business_ids_json
                    ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 0, ?, ?)
                    """,
                    incoming.fingerprint(), incoming.title(), incoming.level(), incoming.kind(),
                    incoming.firstSeen().toString(), incoming.lastSeen().toString(),
                    incoming.hasCustomFrame() ? 1 : 0, incoming.lastMessage(),
                    JsonMaps.write(mapper, incoming.lastBusinessIds())
            );
            return findIssue(incoming.fingerprint()).orElse(incoming);
        }
        String level = rank(incoming.level()) >= rank(existing.level()) ? incoming.level() : existing.level();
        boolean custom = existing.hasCustomFrame() || incoming.hasCustomFrame();
        jdbc.update(
                """
                UPDATE issues SET
                    title = ?, level = ?, kind = ?, count = count + 1, last_seen = ?,
                    has_custom_frame = ?, last_message = ?, last_business_ids_json = ?
                WHERE fingerprint = ?
                """,
                incoming.title(), level, incoming.kind(), incoming.lastSeen().toString(),
                custom ? 1 : 0, incoming.lastMessage(),
                JsonMaps.write(mapper, incoming.lastBusinessIds()), incoming.fingerprint()
        );
        return findIssue(incoming.fingerprint()).orElseThrow();
    }

    public Optional<StoredIssue> findIssueInRun(String fingerprint, long runId) {
        return jdbc.query(
                """
                SELECT
                    i.fingerprint, i.title, i.level, i.kind, COUNT(e.id) AS count,
                    MIN(e.ts) AS first_seen, MAX(e.ts) AS last_seen,
                    i.has_custom_frame, i.muted,
                    (SELECT e2.message FROM events e2
                     WHERE e2.run_id = e.run_id AND e2.fingerprint = i.fingerprint
                     ORDER BY e2.id DESC LIMIT 1) AS last_message,
                    i.last_business_ids_json
                FROM events e
                JOIN issues i ON i.fingerprint = e.fingerprint
                WHERE e.run_id = ? AND i.fingerprint = ?
                GROUP BY i.fingerprint
                """,
                issueMapper(),
                runId,
                fingerprint
        ).stream().findFirst();
    }

    public Optional<StoredIssue> findIssue(String fingerprint) {
        List<StoredIssue> rows = jdbc.query("SELECT * FROM issues WHERE fingerprint = ?", issueMapper(), fingerprint);
        return rows.stream().findFirst();
    }

    public void setMuted(String fingerprint, boolean muted) {
        jdbc.update("UPDATE issues SET muted = ? WHERE fingerprint = ?", muted ? 1 : 0, fingerprint);
    }

    public Optional<String> findSetting(String key) {
        List<String> rows = jdbc.query(
                "SELECT value FROM settings WHERE key = ?",
                (rs, rowNum) -> rs.getString(1),
                key
        );
        return rows.stream().findFirst();
    }

    public void putSetting(String key, String value) {
        jdbc.update(
                """
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """,
                key,
                value
        );
    }

    public List<StoredIssue> listIssues(
            Long runId,
            String level,
            String kind,
            String q,
            String bizKey,
            String bizValue,
            boolean includeMuted
    ) {
        if (runId != null && runId > 0) {
            return listIssuesForRun(runId, level, kind, q, bizKey, bizValue, includeMuted);
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM issues WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
            sql.append(" AND level = ?");
            args.add(level.toUpperCase(Locale.ROOT));
        }
        if (kind != null && !kind.isBlank() && !"ALL".equalsIgnoreCase(kind)) {
            sql.append(" AND kind = ?");
            args.add(kind.toUpperCase(Locale.ROOT));
        }
        if (!includeMuted) {
            sql.append(" AND muted = 0");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (title LIKE ? OR last_message LIKE ? OR fingerprint LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        appendJsonContains(sql, args, "last_business_ids_json", bizKey, bizValue);
        sql.append(" ORDER BY last_seen DESC");
        return jdbc.query(sql.toString(), issueMapper(), args.toArray());
    }

    public List<StoredIssue> listIssuesForRun(
            long runId,
            String level,
            String kind,
            String q,
            String bizKey,
            String bizValue,
            boolean includeMuted
    ) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT
                    i.fingerprint,
                    i.title,
                    i.level,
                    i.kind,
                    COUNT(e.id) AS count,
                    MIN(e.ts) AS first_seen,
                    MAX(e.ts) AS last_seen,
                    i.has_custom_frame,
                    i.muted,
                    (
                        SELECT e2.message FROM events e2
                        WHERE e2.run_id = e.run_id AND e2.fingerprint = i.fingerprint
                        ORDER BY e2.id DESC LIMIT 1
                    ) AS last_message,
                    i.last_business_ids_json
                FROM events e
                JOIN issues i ON i.fingerprint = e.fingerprint
                WHERE e.run_id = ?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(runId);
        if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
            sql.append(" AND e.level = ?");
            args.add(level.toUpperCase(Locale.ROOT));
        }
        if (kind != null && !kind.isBlank() && !"ALL".equalsIgnoreCase(kind)) {
            sql.append(" AND i.kind = ?");
            args.add(kind.toUpperCase(Locale.ROOT));
        }
        if (!includeMuted) {
            sql.append(" AND i.muted = 0");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (i.title LIKE ? OR i.last_message LIKE ? OR i.fingerprint LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String needle = BusinessIdJson.containsNeedle(mapper, bizKey, bizValue);
        if (needle != null) {
            sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM events bx
                        WHERE bx.run_id = e.run_id
                          AND bx.fingerprint = i.fingerprint
                          AND instr(bx.business_ids_json, ?) > 0
                    )
                    """);
            args.add(needle);
        }
        sql.append(" GROUP BY i.fingerprint ORDER BY MAX(e.ts) DESC");
        return jdbc.query(sql.toString(), issueMapper(), args.toArray());
    }

    private void appendJsonContains(
            StringBuilder sql,
            List<Object> args,
            String column,
            String bizKey,
            String bizValue
    ) {
        String needle = BusinessIdJson.containsNeedle(mapper, bizKey, bizValue);
        if (needle == null) {
            return;
        }
        sql.append(" AND instr(").append(column).append(", ?) > 0");
        args.add(needle);
    }

    public List<StoredEvent> listEventsForFingerprint(String fingerprint, Long runId, int limit) {
        if (runId != null && runId > 0) {
            return jdbc.query(
                    "SELECT * FROM events WHERE fingerprint = ? AND run_id = ? ORDER BY id DESC LIMIT ?",
                    eventMapper(), fingerprint, runId, limit
            );
        }
        return jdbc.query(
                "SELECT * FROM events WHERE fingerprint = ? ORDER BY id DESC LIMIT ?",
                eventMapper(), fingerprint, limit
        );
    }

    public List<StoredEvent> searchEvents(String level, String q, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
            sql.append(" AND level = ?");
            args.add(level.toUpperCase(Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (message LIKE ? OR logger LIKE ? OR exception LIKE ? OR raw_text LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), eventMapper(), args.toArray());
    }

    private RowMapper<StoredRun> runMapper() {
        return (rs, rowNum) -> new StoredRun(
                rs.getLong("id"),
                rs.getString("hybris_home"),
                rs.getString("log_path"),
                parseInstant(rs.getString("started_at")),
                parseInstant(rs.getString("ended_at")),
                rs.getString("mode")
        );
    }

    private RowMapper<StoredIssue> issueMapper() {
        return (rs, rowNum) -> new StoredIssue(
                rs.getString("fingerprint"),
                rs.getString("title"),
                rs.getString("level"),
                rs.getString("kind"),
                rs.getLong("count"),
                parseInstant(rs.getString("first_seen")),
                parseInstant(rs.getString("last_seen")),
                rs.getInt("has_custom_frame") == 1,
                rs.getInt("muted") == 1,
                rs.getString("last_message"),
                JsonMaps.read(mapper, rs.getString("last_business_ids_json"))
        );
    }

    private RowMapper<StoredEvent> eventMapper() {
        return (rs, rowNum) -> new StoredEvent(
                rs.getLong("id"),
                rs.getLong("run_id"),
                parseInstant(rs.getString("ts")),
                rs.getString("level"),
                rs.getString("logger"),
                rs.getString("thread"),
                rs.getString("message"),
                rs.getString("exception"),
                rs.getString("fingerprint"),
                rs.getString("raw_text"),
                rs.getString("context_text"),
                rs.getString("kind"),
                rs.getInt("has_custom_frame") == 1,
                JsonMaps.read(mapper, rs.getString("business_ids_json"))
        );
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw);
    }

    private static int rank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "FATAL", "ERROR" -> 2;
            case "WARN" -> 1;
            default -> 0;
        };
    }
}
