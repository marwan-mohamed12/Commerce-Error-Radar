package com.commerce.radar.store;

import com.commerce.radar.config.RadarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbc;
    private final RadarProperties properties;

    public SchemaInitializer(JdbcTemplate jdbc, RadarProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        Path db = SqliteFile.resolve(properties.getSqlitePath());
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("PRAGMA busy_timeout=5000");
        jdbc.execute("PRAGMA foreign_keys=ON");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hybris_home TEXT,
                    log_path TEXT,
                    started_at TEXT NOT NULL,
                    ended_at TEXT,
                    mode TEXT NOT NULL DEFAULT 'LIVE'
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_id INTEGER NOT NULL,
                    ts TEXT NOT NULL,
                    level TEXT NOT NULL,
                    logger TEXT,
                    thread TEXT,
                    message TEXT,
                    exception TEXT,
                    fingerprint TEXT NOT NULL,
                    raw_text TEXT,
                    context_text TEXT,
                    kind TEXT,
                    has_custom_frame INTEGER NOT NULL DEFAULT 0,
                    business_ids_json TEXT,
                    FOREIGN KEY (run_id) REFERENCES runs(id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS issues (
                    fingerprint TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    level TEXT NOT NULL,
                    kind TEXT,
                    count INTEGER NOT NULL,
                    first_seen TEXT NOT NULL,
                    last_seen TEXT NOT NULL,
                    has_custom_frame INTEGER NOT NULL DEFAULT 0,
                    muted INTEGER NOT NULL DEFAULT 0,
                    last_message TEXT,
                    last_business_ids_json TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_events_fingerprint ON events(fingerprint)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_events_level ON events(level)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_issues_last_seen ON issues(last_seen)");
        log.info("SQLite ready at {}", db.toAbsolutePath());
    }
}
