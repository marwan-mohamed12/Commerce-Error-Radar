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
}
