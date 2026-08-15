package com.commerce.radar.adapter.tail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceResolverTest {

    @TempDir
    Path temp;

    @Test
    void emptyHomeUsesSampleLog() throws Exception {
        Path samples = temp.resolve("sample-logs");
        Files.createDirectories(samples);
        Path sample = samples.resolve("console-20260809.log");
        Files.writeString(sample, "demo");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(null, List.of(samples));

        assertTrue(decision.demo());
        assertEquals(sample, decision.file());
        assertTrue(decision.message().startsWith("DEMO"));
        assertTrue(decision.message().contains(sample.toAbsolutePath().normalize().toString()));
    }

    @Test
    void blankHomeUsesSampleLog() throws Exception {
        Path samples = temp.resolve("sample-logs");
        Files.createDirectories(samples);
        Path sample = samples.resolve("console-20260809.log");
        Files.writeString(sample, "demo");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(Path.of(""), List.of(samples));

        assertTrue(decision.demo());
        assertEquals(sample, decision.file());
    }

    @Test
    void setHomeNeverFallsBackToSampleEvenWhenNoLiveLog() throws Exception {
        Path home = temp.resolve("hybris-home");
        Files.createDirectories(home);
        Path samples = temp.resolve("sample-logs");
        Files.createDirectories(samples);
        Files.writeString(samples.resolve("console-20260809.log"), "demo");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(home, List.of(samples));

        assertTrue(decision.live());
        assertFalse(decision.demo());
        assertNull(decision.file());
        assertTrue(decision.message().contains("Watching"));
    }

    @Test
    void setHomeTailsLiveConsoleUnderHybrisFolder() throws Exception {
        Path home = temp.resolve("hybris");
        Path tomcat = home.resolve("log").resolve("tomcat");
        Files.createDirectories(tomcat);
        Path live = tomcat.resolve("console-20260809.log");
        Files.writeString(live, "live");
        Path samples = temp.resolve("sample-logs");
        Files.createDirectories(samples);
        Files.writeString(samples.resolve("console-20260809.log"), "demo");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(home, List.of(samples));

        assertTrue(decision.live());
        assertEquals(live, decision.file());
        assertTrue(decision.message().startsWith("LIVE"));
        assertTrue(decision.message().contains(live.toAbsolutePath().normalize().toString()));
    }

    @Test
    void setHomeTailsLiveConsoleWhenHomeIsPlatformRoot() throws Exception {
        Path home = temp.resolve("core-customize");
        Path tomcat = home.resolve("hybris").resolve("log").resolve("tomcat");
        Files.createDirectories(tomcat);
        Path live = tomcat.resolve("console-20260809.log");
        Files.writeString(live, "live");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(home, List.of());

        assertTrue(decision.live());
        assertEquals(live, decision.file());
    }

    @Test
    void setHomeTailsAntLogWhenThatIsTheOnlyFile() throws Exception {
        Path home = temp.resolve("hybris");
        Path log = home.resolve("log");
        Files.createDirectories(log);
        Path ant = log.resolve("ant.log");
        Files.writeString(ant, "BUILD FAILED");

        LogSourceResolver.Decision decision = LogSourceResolver.resolve(home, List.of());

        assertTrue(decision.live());
        assertEquals(ant, decision.file());
        assertTrue(decision.message().startsWith("LIVE"));
    }
}
