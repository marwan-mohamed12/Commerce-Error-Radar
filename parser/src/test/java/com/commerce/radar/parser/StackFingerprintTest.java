package com.commerce.radar.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackFingerprintTest {

    private final StackFingerprint fingerprint = new StackFingerprint("com.yourcompany");

    @Test
    void prefersCustomPackageFrame() {
        String stack = """
                java.lang.NullPointerException
                	at de.hybris.platform.commercefacades.order.impl.DefaultCartFacade.addToCart(DefaultCartFacade.java:210)
                	at com.yourcompany.facades.impl.DefaultCartFacade.addToCart(DefaultCartFacade.java:142)
                	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:255)
                """;
        StackFingerprint.Result result = fingerprint.compute("NullPointerException", stack);
        assertTrue(result.hasCustomFrame());
        assertEquals("NullPointerException@com.yourcompany.facades.impl.DefaultCartFacade.addToCart", result.fingerprint());
    }

    @Test
    void fallsBackToFirstNonFrameworkFrame() {
        String stack = """
                java.lang.IllegalStateException
                	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089)
                	at org.example.other.VendorFilter.doFilter(VendorFilter.java:44)
                	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195)
                """;
        StackFingerprint.Result result = fingerprint.compute("IllegalStateException", stack);
        assertFalse(result.hasCustomFrame());
        assertEquals("IllegalStateException@org.example.other.VendorFilter.doFilter", result.fingerprint());
    }

    @Test
    void neverKeysOffHybrisWhenOnlyHybrisAndFrameworkFramesExist() {
        String stack = """
                java.lang.IllegalStateException
                	at de.hybris.platform.servicelayer.session.impl.DefaultSessionService.setAttribute(DefaultSessionService.java:188)
                	at de.hybris.platform.jalo.JaloSession.setAttribute(JaloSession.java:1455)
                	at org.apache.catalina.session.StandardSession.setAttribute(StandardSession.java:1435)
                	at java.base/java.lang.Thread.run(Thread.java:1583)
                """;
        StackFingerprint.Result result = fingerprint.compute("IllegalStateException", stack);
        assertFalse(result.fingerprint().contains("de.hybris."));
        assertEquals("IllegalStateException@hybris", result.fingerprint());
    }
}
