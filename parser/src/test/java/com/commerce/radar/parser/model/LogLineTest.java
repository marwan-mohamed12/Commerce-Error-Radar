package com.commerce.radar.parser.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLineTest {

    @Test
    void unwrapsWrapperPrefixAndKeepsPlainLines() {
        String wrapped = "INFO   | jvm 1    | main    | 2026/08/10 00:15:03.228 | ERROR [hybrisHTTP23] [Foo] boom";
        assertEquals("ERROR [hybrisHTTP23] [Foo] boom", LogLine.unwrapWrapper(wrapped));
        assertEquals("ERROR [hybrisHTTP23] [Foo] boom", LogLine.unwrapWrapper("ERROR [hybrisHTTP23] [Foo] boom"));
    }

    @Test
    void wrapperErrorIsAHeaderAndPlainDebugIsNotAnEventLevel() {
        String wrappedError = "INFO   | jvm 1    | main    | 2026/08/10 00:15:03.228 | ERROR [hybrisHTTP23] [Foo] boom";
        String wrappedDebug = "INFO   | jvm 1    | main    | 2026/08/10 00:13:41.008 | DEBUG [hybrisHTTP23] [SapCartCalculationService] Calculate the cart";

        assertTrue(LogLine.isErrorOrWarn(wrappedError));
        assertFalse(LogLine.isErrorOrWarn(wrappedDebug));

        LogLine error = LogLine.parseHeader(wrappedError).orElseThrow();
        assertEquals("ERROR", error.level());
        assertEquals("Foo", error.logger());
        assertEquals("hybrisHTTP23", error.thread());
        assertEquals("boom", error.message());
        assertTrue(error.isErrorOrWarnLevel());

        LogLine debug = LogLine.parseHeader(wrappedDebug).orElseThrow();
        assertEquals("DEBUG", debug.level());
        assertFalse(debug.isErrorOrWarnLevel());
    }

    @Test
    void unwrapsShortWrapperAndAntPrefix() {
        assertEquals(
                "JVM exited unexpectedly.",
                LogLine.unwrapWrapper("ERROR  | wrapper  | 2026/08/10 00:06:18.000 | JVM exited unexpectedly.")
        );
        assertEquals(
                "ERROR [main] [DefaultSetup] boom",
                LogLine.unwrapWrapper("     [java] ERROR [main] [DefaultSetup] boom")
        );
    }

    @Test
    void parsesCatalinaJulAndBuildFailed() {
        LogLine catalina = LogLine.parseHeader(
                "15-Aug-2026 10:22:05.123 SEVERE [main] org.apache.catalina.core.StandardContext.startInternal startup failed"
        ).orElseThrow();
        assertEquals("ERROR", catalina.level());
        assertEquals("main", catalina.thread());
        assertTrue(catalina.logger().contains("StandardContext"));
        assertTrue(catalina.message().contains("startup failed"));

        LogLine jul = LogLine.parseHeader("SEVERE: Context [/store] startup failed").orElseThrow();
        assertEquals("ERROR", jul.level());
        assertEquals("catalina", jul.logger());

        LogLine failed = LogLine.parseHeader("BUILD FAILED").orElseThrow();
        assertEquals("ERROR", failed.level());
        assertEquals("ant", failed.logger());
        assertTrue(failed.isErrorOrWarnLevel());

        LogLine ok = LogLine.parseHeader("BUILD SUCCESSFUL").orElseThrow();
        assertEquals("INFO", ok.level());
        assertFalse(ok.isErrorOrWarnLevel());
    }

    @Test
    void wrapperOnlyErrorIsAHeader() {
        LogLine line = LogLine.parseHeader(
                "ERROR  | wrapper  | 2026/08/10 00:06:18.000 | JVM exited unexpectedly."
        ).orElseThrow();
        assertEquals("ERROR", line.level());
        assertEquals("wrapper", line.logger());
        assertEquals("JVM exited unexpectedly.", line.message());
        assertTrue(LogLine.isErrorOrWarn(
                "ERROR  | wrapper  | 2026/08/10 00:06:18.000 | JVM exited unexpectedly."));
    }
}
