package com.commerce.radar.adapter.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsToastXmlTest {

    @Test
    void escapesMarkupInTitleAndBody() {
        String xml = WindowsToastXml.document("Radar · ERROR & OCC", "<script>alert('x')</script>");
        assertTrue(xml.contains("Radar · ERROR &amp; OCC"));
        assertTrue(xml.contains("&lt;script&gt;alert(&apos;x&apos;)&lt;/script&gt;"));
        assertFalse(xml.contains("<script>"));
    }

    @Test
    void emptyTextBecomesEmptyElement() {
        assertEquals("", WindowsToastXml.escape(null));
        assertTrue(WindowsToastXml.document("", "").contains("<text></text>"));
    }
}
