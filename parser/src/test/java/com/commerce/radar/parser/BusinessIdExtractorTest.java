package com.commerce.radar.parser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BusinessIdExtractorTest {

    @Test
    void extractsOrderProductUserCronAndCatalog() {
        Map<String, String> ids = BusinessIdExtractor.extract(
                "Failed for order 00001234 productCode=23355 userId=customer@shop.test",
                "CronJob solrIncrementalUpdate failed unknown catalog version electronicsProductCatalog:Staged",
                "importing products-delta.impex"
        );
        assertEquals("00001234", ids.get("order"));
        assertEquals("23355", ids.get("product"));
        assertEquals("customer@shop.test", ids.get("user"));
        assertEquals("solrIncrementalUpdate", ids.get("cronjob"));
        assertEquals("electronicsProductCatalog:Staged", ids.get("catalogVersion"));
        assertEquals("products-delta.impex", ids.get("impex"));
    }

    @Test
    void ignoresNullTokens() {
        Map<String, String> ids = BusinessIdExtractor.extract("productCode=null order=true");
        assertFalse(ids.containsKey("product"));
        assertFalse(ids.containsKey("order"));
    }
}
