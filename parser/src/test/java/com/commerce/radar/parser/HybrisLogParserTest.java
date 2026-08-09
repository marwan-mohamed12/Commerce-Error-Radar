package com.commerce.radar.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybrisLogParserTest {

    @Test
    void parsesOccControllerStack() {
        ParsedEvent event = onlyEvent("occ-npe.log");
        assertEquals("ERROR", event.level());
        assertEquals("DefaultCartFacade", event.logger());
        assertEquals("hybrisHTTP23", event.thread());
        assertTrue(event.message().contains("Failed to add product"));
        assertEquals("NullPointerException", event.exceptionType());
        assertTrue(event.stackText().contains("at com.yourcompany.facades.impl.DefaultCartFacade.addToCart"));
        assertEquals(IssueKind.OCC, event.kind());
        assertEquals("23355", event.businessIds().get("product"));
    }

    @Test
    void parsesCronJobFailure() {
        ParsedEvent event = onlyEvent("cronjob-failure.log");
        assertEquals("ERROR", event.level());
        assertEquals(IssueKind.CRONJOB, event.kind());
        assertEquals("ModelNotFoundException", event.exceptionType());
        assertTrue(event.hasCustomFrame());
        assertTrue(event.fingerprint().contains("com.yourcompany.solr.jobs.IncrementalIndexJob.perform"));
        assertTrue(event.businessIds().containsKey("cronjob"));
    }

    @Test
    void parsesImpexAndFollowingWarn() {
        List<ParsedEvent> events = parseFixture("impex-error.log");
        assertEquals(2, events.size());

        ParsedEvent error = events.get(0);
        assertEquals("ERROR", error.level());
        assertEquals(IssueKind.IMPEX, error.kind());
        assertEquals("ImpExException", error.exceptionType());
        assertEquals("electronicsProductCatalog:Online", error.businessIds().get("catalogVersion"));
        assertEquals("products-delta.impex", error.businessIds().get("impex"));

        ParsedEvent warn = events.get(1);
        assertEquals("WARN", warn.level());
        assertEquals(IssueKind.IMPEX, warn.kind());
        assertTrue(warn.message().contains("products-delta.impex"));
    }

    @Test
    void parsesWarnWithoutExceptionTypeLine() {
        ParsedEvent event = onlyEvent("warn-solr.log");
        assertEquals("WARN", event.level());
        assertEquals(IssueKind.SOLR, event.kind());
        assertTrue(event.message().toLowerCase().contains("solr ping"));
    }

    @Test
    void identicalNpesShareFingerprint() {
        ParsedEvent first = onlyEvent("occ-npe.log");
        ParsedEvent second = onlyEvent("occ-npe-repeat.log");
        assertEquals(first.fingerprint(), second.fingerprint());
        assertTrue(first.hasCustomFrame());
        assertTrue(second.hasCustomFrame());
        assertTrue(first.fingerprint().startsWith("NullPointerException@com.yourcompany.facades.impl.DefaultCartFacade.addToCart"));
    }

    @Test
    void hybrisOnlyStackDoesNotFingerprintOnHybrisFrame() {
        ParsedEvent event = onlyEvent("hybris-only-stack.log");
        assertFalse(event.hasCustomFrame());
        assertFalse(event.fingerprint().contains("de.hybris."));
        assertEquals("IllegalStateException@hybris", event.fingerprint());
    }

    @Test
    void unwrapsTanukiWrapperConsoleAndIgnoresInfoDebug() {
        ParsedEvent event = onlyEvent("wrapper-occ-npe.log");
        assertEquals("ERROR", event.level());
        assertEquals("DefaultCartFacade", event.logger());
        assertEquals("hybrisHTTP23", event.thread());
        assertTrue(event.message().contains("Failed to add product"));
        assertEquals("NullPointerException", event.exceptionType());
        assertTrue(event.hasCustomFrame());
        assertTrue(event.fingerprint().contains("com.yourcompany.facades.impl.DefaultCartFacade.addToCart"));
        assertFalse(event.rawText().contains("jvm 1"));
    }

    @Test
    void timestampedHeadersAreAccepted() {
        HybrisLogParser parser = new HybrisLogParser("com.yourcompany");
        List<String> lines = List.of(
                "2026-08-09 14:32:01,234 ERROR [hybrisHTTP1] [FooController] boom",
                "java.lang.IllegalArgumentException: bad id",
                "\tat com.yourcompany.occ.FooController.get(FooController.java:10)",
                "\tat de.hybris.platform.web.Filter.doFilter(Filter.java:1)",
                "2026-08-09 14:32:01,400 INFO  [hybrisHTTP1] [FooController] done"
        );
        List<ParsedEvent> events = parser.parseAll(lines);
        assertEquals(1, events.size());
        assertEquals("IllegalArgumentException", events.get(0).exceptionType());
        assertTrue(events.get(0).fingerprint().contains("com.yourcompany.occ.FooController.get"));
    }

    private static ParsedEvent onlyEvent(String fixture) {
        List<ParsedEvent> events = parseFixture(fixture);
        assertEquals(1, events.size(), "expected a single event in " + fixture + " but got " + events.size());
        return events.get(0);
    }

    private static List<ParsedEvent> parseFixture(String name) {
        try {
            var url = HybrisLogParserTest.class.getResource("/fixtures/" + name);
            if (url == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            Path path = Path.of(url.toURI());
            List<String> lines = Files.readAllLines(path);
            return new HybrisLogParser("com.yourcompany").parseAll(lines);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
