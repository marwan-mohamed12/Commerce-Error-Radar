package com.commerce.radar.adapter.notify;

import com.commerce.radar.application.ErrorNotification;
import com.commerce.radar.application.ErrorToaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Windows Action Center toast via PowerShell + WinRT. Does not touch AWT / headless.
 */
@Component
public class WindowsToastNotifier implements ErrorToaster {

    private static final Logger log = LoggerFactory.getLogger(WindowsToastNotifier.class);
    private static final String APP_ID = "Commerce.Error.Radar";
    private static final long TOAST_GAP_MS = 8_000L;

    private final AtomicLong lastShownAt = new AtomicLong(0L);
    private final boolean windows;

    public WindowsToastNotifier() {
        this(System.getProperty("os.name", ""));
    }

    WindowsToastNotifier(String osName) {
        this.windows = osName.toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public boolean available() {
        return windows;
    }

    @Override
    public void show(ErrorNotification notification) {
        if (!windows || notification == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastShownAt.get();
        if (now - previous < TOAST_GAP_MS) {
            return;
        }
        if (!lastShownAt.compareAndSet(previous, now)) {
            return;
        }
        String title = notification.toastTitle();
        String body = notification.message();
        Thread.ofVirtual().name("radar-toast").start(() -> fire(title, body));
    }

    private void fire(String title, String body) {
        String xml = WindowsToastXml.document(title, body);
        String script = """
                $ErrorActionPreference = 'Stop'
                [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
                $doc = New-Object Windows.Data.Xml.Dom.XmlDocument
                $doc.LoadXml(@'
                %s
                '@)
                $toast = [Windows.UI.Notifications.ToastNotification]::new($doc)
                [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('%s').Show($toast)
                """.formatted(xml, APP_ID);
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle", "Hidden",
                "-EncodedCommand", encoded
        );
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.debug("Windows toast timed out");
            } else if (process.exitValue() != 0) {
                log.debug("Windows toast exited {}", process.exitValue());
            }
        } catch (Exception e) {
            log.debug("Windows toast failed: {}", e.toString());
        }
    }
}
