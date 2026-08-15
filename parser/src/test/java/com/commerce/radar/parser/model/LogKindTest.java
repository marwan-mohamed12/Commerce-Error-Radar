package com.commerce.radar.parser.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogKindTest {

    @Test
    void classifiesHybrisLogFileNames() {
        assertEquals(LogKind.CONSOLE, LogKind.fromFileName("console-20260815.log"));
        assertEquals(LogKind.CATALINA, LogKind.fromFileName("catalina.out"));
        assertEquals(LogKind.CATALINA, LogKind.fromFileName("catalina.2026-08-15.log"));
        assertEquals(LogKind.WRAPPER, LogKind.fromFileName("wrapper.log"));
        assertEquals(LogKind.ANT, LogKind.fromFileName("ant.log"));
        assertEquals(LogKind.LOCALHOST, LogKind.fromFileName("localhost.2026-08-15.log"));
        assertEquals(LogKind.UNKNOWN, LogKind.fromFileName("localhost_access_log.2026-08-15.txt"));
        assertEquals(LogKind.UNKNOWN, LogKind.fromFileName("notes.txt"));
        assertTrue(LogKind.fromPath(Path.of("D:/hybris/log/tomcat/console-20260815.log")).interesting());
        assertFalse(LogKind.fromFileName("localhost_access_log.txt").interesting());
    }
}
