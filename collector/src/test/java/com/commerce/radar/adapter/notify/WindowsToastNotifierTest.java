package com.commerce.radar.adapter.notify;

import com.commerce.radar.application.ErrorNotification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsToastNotifierTest {

    @Test
    void ignoredOnNonWindows() {
        WindowsToastNotifier notifier = new WindowsToastNotifier("Linux");
        assertFalse(notifier.available());
        notifier.show(ErrorNotification.confirmation());
    }

    @Test
    void availableOnWindows() {
        assertTrue(new WindowsToastNotifier("Windows 11").available());
    }

    @Test
    void scriptRegistersCustomAumidAndEmbedsXml() {
        String xml = WindowsToastXml.document("Radar · ERROR · OCC", "boom");
        String script = WindowsToastNotifier.script(WindowsToastNotifier.APP_ID, xml, true);
        assertTrue(script.contains("HKCU:\\Software\\Classes\\AppUserModelId\\Commerce.Error.Radar"));
        assertTrue(script.contains("CreateToastNotifier('Commerce.Error.Radar')"));
        assertTrue(script.contains(xml));
    }

    @Test
    void fallbackScriptSkipsRegistration() {
        String script = WindowsToastNotifier.script(WindowsToastNotifier.POWERSHELL_AUMID, "<toast/>", false);
        assertFalse(script.contains("New-Item"));
        assertTrue(script.contains(WindowsToastNotifier.POWERSHELL_AUMID));
    }
}
