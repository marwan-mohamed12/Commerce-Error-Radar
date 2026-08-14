package com.commerce.radar.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) {
        Path db = Path.of("target", "radar-openapi-test.db");
        registry.add("radar.sqlite-path", () -> db.toAbsolutePath().toString());
        registry.add("radar.hybris-home", () -> "");
        registry.add("spring.devtools.restart.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void documentsEveryCollectorEndpoint() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
        Set<String> documented = new HashSet<>();
        paths.fieldNames().forEachRemaining(documented::add);
        assertTrue(documented.contains("/api/runs"), documented.toString());
        assertTrue(documented.contains("/api/runs/current"), documented.toString());
        assertTrue(documented.contains("/api/runs/open"), documented.toString());
        assertTrue(documented.contains("/api/issues"), documented.toString());
        assertTrue(documented.contains("/api/issues/one"), documented.toString());
        assertTrue(documented.contains("/api/issues/mute"), documented.toString());
        assertTrue(documented.contains("/api/events"), documented.toString());
        assertTrue(documented.contains("/api/notify"), documented.toString());
        assertTrue(documented.contains("/api/notify/presence"), documented.toString());
        assertTrue(documented.contains("/api/stream"), documented.toString());
    }
}
