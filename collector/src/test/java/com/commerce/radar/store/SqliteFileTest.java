package com.commerce.radar.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteFileTest {

    @TempDir
    Path temp;

    @Test
    void keepsFilePathAndCreatesParent() throws Exception {
        Path file = temp.resolve("data").resolve("radar.db");
        Path resolved = SqliteFile.resolve(file);
        assertEquals(file.toAbsolutePath().normalize(), resolved);
        assertTrue(Files.isDirectory(file.getParent()));
        assertFalse(Files.isDirectory(resolved));
    }

    @Test
    void directoryBecomesRadarDbInsideIt() throws Exception {
        Path dir = temp.resolve("data");
        Files.createDirectories(dir);
        Path resolved = SqliteFile.resolve(dir);
        assertEquals(dir.resolve("radar.db"), resolved);
        assertTrue(SqliteFile.jdbcUrl(resolved).startsWith("jdbc:sqlite:file:"));
        assertTrue(SqliteFile.jdbcUrl(resolved).endsWith("/radar.db")
                || SqliteFile.jdbcUrl(resolved).endsWith("radar.db"));
    }
}
