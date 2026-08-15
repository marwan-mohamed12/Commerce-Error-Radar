package com.commerce.radar.adapter.tail;

import com.commerce.radar.parser.model.LogKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybrisLogLocatorTest {

    @TempDir
    Path temp;

    @Test
    void findsEveryHybrisLogKindAndSkipsAccessLogs() throws Exception {
        Path log = temp.resolve("hybris").resolve("log");
        Path tomcat = log.resolve("tomcat");
        Files.createDirectories(tomcat);
        Path console = tomcat.resolve("console-20260815.log");
        Path catalina = tomcat.resolve("catalina.2026-08-15.log");
        Path wrapper = tomcat.resolve("wrapper.log");
        Path localhost = tomcat.resolve("localhost.2026-08-15.log");
        Path access = tomcat.resolve("localhost_access_log.2026-08-15.txt");
        Path ant = log.resolve("ant.log");
        Files.writeString(console, "console");
        Files.writeString(catalina, "catalina");
        Files.writeString(wrapper, "wrapper");
        Files.writeString(localhost, "localhost");
        Files.writeString(access, "GET /");
        Files.writeString(ant, "BUILD FAILED");

        Files.setLastModifiedTime(ant, FileTime.from(Instant.parse("2026-08-15T12:00:00Z")));
        Files.setLastModifiedTime(console, FileTime.from(Instant.parse("2026-08-15T11:00:00Z")));
        Files.setLastModifiedTime(catalina, FileTime.from(Instant.parse("2026-08-15T10:00:00Z")));

        List<HybrisLogLocator.DiscoveredLog> found = HybrisLogLocator.listAll(temp);
        assertEquals(5, found.size());
        assertEquals(ant, HybrisLogLocator.newest(temp).orElseThrow());
        assertEquals(LogKind.ANT, found.getFirst().kind());
        assertTrue(found.stream().anyMatch(item -> item.kind() == LogKind.CONSOLE));
        assertTrue(found.stream().anyMatch(item -> item.kind() == LogKind.CATALINA));
        assertTrue(found.stream().anyMatch(item -> item.kind() == LogKind.WRAPPER));
        assertTrue(found.stream().anyMatch(item -> item.kind() == LogKind.LOCALHOST));
        assertTrue(found.stream().noneMatch(item -> item.fileName().contains("access")));
        var byKind = HybrisLogLocator.newestByKind(temp);
        assertEquals(5, byKind.size());
        assertEquals(ant, byKind.get(LogKind.ANT).path());
        assertEquals(console, byKind.get(LogKind.CONSOLE).path());
    }

    @Test
    void newestOfKindIgnoresOtherFiles() throws Exception {
        Path tomcat = temp.resolve("log").resolve("tomcat");
        Files.createDirectories(tomcat);
        Path older = tomcat.resolve("console-20260801.log");
        Path newer = tomcat.resolve("console-20260815.log");
        Path ant = temp.resolve("log").resolve("ant.log");
        Files.writeString(older, "old");
        Files.writeString(newer, "new");
        Files.writeString(ant, "ant");
        Files.setLastModifiedTime(ant, FileTime.from(Instant.parse("2026-08-15T18:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-08-15T12:00:00Z")));
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-08-01T00:00:00Z")));

        assertEquals(ant, HybrisLogLocator.newest(temp).orElseThrow());
        assertEquals(newer, HybrisLogLocator.newestOfKind(temp, LogKind.CONSOLE, null).orElseThrow());
        assertEquals(older, HybrisLogLocator.newestOfKind(temp, LogKind.CONSOLE, newer).orElseThrow());
    }
}
