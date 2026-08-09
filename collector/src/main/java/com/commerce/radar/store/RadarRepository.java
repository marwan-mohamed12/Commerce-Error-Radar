package com.commerce.radar.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO runs (hybris_home, log_path, started_at, mode) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, hybrisHome);
            ps.setString(2, logPath);
            ps.setString(3, now.toString());
            ps.setString(4, mode);
            return ps;
        }, keys);
        Number id = keys.getKey();
        long runId = id == null ? 0L : id.longValue();
        return new StoredRun(runId, hybrisHome, logPath, now, null, mode);
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

    public Optional<StoredIssue> findIssue(String fingerprint) {
        List<StoredIssue> rows = jdbc.query("SELECT * FROM issues WHERE fingerprint = ?", issueMapper(), fingerprint);
        return rows.stream().findFirst();
    }

    public void setMuted(String fingerprint, boolean muted) {
        jdbc.update("UPDATE issues SET muted = ? WHERE fingerprint = ?", muted ? 1 : 0, fingerprint);
    }

    public List<StoredIssue> listIssues(String level, String kind, String q, boolean mineOnly, boolean includeMuted) {
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
        if (mineOnly) {
            sql.append(" AND has_custom_frame = 1");
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
        sql.append(" ORDER BY last_seen DESC");
        return jdbc.query(sql.toString(), issueMapper(), args.toArray());
    }

    public List<StoredEvent> listEventsForFingerprint(String fingerprint, int limit) {
        return jdbc.query(
                "SELECT * FROM events WHERE fingerprint = ? ORDER BY id DESC LIMIT ?",
                eventMapper(), fingerprint, limit
        );
    }

    public List<StoredEvent> searchEvents(String level, String q, boolean mineOnly, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
            sql.append(" AND level = ?");
            args.add(level.toUpperCase(Locale.ROOT));
        }
        if (mineOnly) {
            sql.append(" AND has_custom_frame = 1");
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
