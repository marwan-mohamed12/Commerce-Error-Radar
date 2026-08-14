package com.commerce.radar.adapter.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite must open a <em>file</em>. If the configured path is a directory
 * ({@code SQLITE_CANTOPEN_ISDIR}), use {@code radar.db} inside it.
 */
public final class SqliteFile {

    private SqliteFile() {
    }

    public static Path resolve(Path configured) throws IOException {
        Path db = configured == null || configured.toString().isBlank()
                ? Path.of("data/radar.db")
                : configured.toAbsolutePath().normalize();
        if (Files.isDirectory(db)) {
            db = db.resolve("radar.db");
        }
        Path parent = db.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.isDirectory(db)) {
            throw new IllegalStateException(
                    "radar.sqlite-path is a directory, not a file: " + db
                            + " — set radar.sqlite-path to something like .../data/radar.db");
        }
        return db;
    }

    public static String jdbcUrl(Path db) {
        String path = db.toAbsolutePath().normalize().toString().replace('\\', '/');
        return "jdbc:sqlite:file:" + path;
    }
}
