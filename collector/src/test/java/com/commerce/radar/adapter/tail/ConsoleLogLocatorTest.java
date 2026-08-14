package com.commerce.radar.adapter.tail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLogLocatorTest {

    @TempDir
    Path temp;

    @Test
    void findsNewestConsoleLogUnderHybrisHome() throws Exception {
        Path tomcat = temp.resolve("hybris").resolve("log").resolve("tomcat");
        Files.createDirectories(tomcat);
        Path older = tomcat.resolve("console-20260101.log");
        Path newer = tomcat.resolve("console-20260809.log");
        Files.writeString(older, "old");
        Files.writeString(newer, "new");
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-08-09T00:00:00Z")));

        assertEquals(tomcat, ConsoleLogLocator.tomcatLogDir(temp));
        assertEquals(newer, ConsoleLogLocator.newestConsoleLog(tomcat).orElseThrow());
    }

    @Test
    void emptyDirectoryYieldsEmpty() throws IOException {
        Path dir = temp.resolve("empty");
        Files.createDirectories(dir);
        assertTrue(ConsoleLogLocator.newestConsoleLog(dir).isEmpty());
    }

    @Test
    void findsConsoleWhenHomeIsTheHybrisFolder() throws Exception {
        Path tomcat = temp.resolve("log").resolve("tomcat");
        Files.createDirectories(tomcat);
        Path log = tomcat.resolve("console-20260809.log");
        Files.writeString(log, "live");

        assertEquals(tomcat, ConsoleLogLocator.tomcatLogDir(temp));
        assertEquals(log, ConsoleLogLocator.newestConsoleLog(tomcat).orElseThrow());
    }

    @Test
    void prefersDirectoryThatAlreadyHasAConsoleLog() throws Exception {
        Path nested = temp.resolve("log").resolve("tomcat");
        Path deeper = temp.resolve("hybris").resolve("log").resolve("tomcat");
        Files.createDirectories(nested);
        Files.createDirectories(deeper);
        Path live = deeper.resolve("console-20260809.log");
        Files.writeString(live, "live");

        assertEquals(deeper, ConsoleLogLocator.tomcatLogDir(temp));
        assertEquals(live, ConsoleLogLocator.newestIn(
                nested,
                deeper
        ).orElseThrow());
    }
}
