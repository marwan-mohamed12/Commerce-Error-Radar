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
 *
 * <p>{@code CreateToastNotifier('Commerce.Error.Radar')} returns without error on an
 * unregistered AUMID, then Windows swallows the toast. Register the id first, and
 * fall back to the built-in PowerShell AUMID when that still fails.
 */
@Component
public class WindowsToastNotifier implements ErrorToaster {

    static final String APP_ID = "Commerce.Error.Radar";
    static final String POWERSHELL_AUMID =
            "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe";

    private static final Logger log = LoggerFactory.getLogger(WindowsToastNotifier.class);
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
        Result first = run(script(APP_ID, xml, true));
        if (first.ok()) {
            return;
        }
        log.warn("Windows toast via {} failed: {}", APP_ID, first.summary());
        Result fallback = run(script(POWERSHELL_AUMID, xml, false));
        if (!fallback.ok()) {
            log.warn("Windows toast fallback failed: {}", fallback.summary());
        }
    }

    static String script(String appId, String xml, boolean register) {
        String registerBlock = register ? REGISTER_BLOCK.replace("{{APP_ID}}", appId) : "";
        return SCRIPT
                .replace("{{REGISTER}}", registerBlock)
                .replace("{{XML}}", xml)
                .replace("{{APP_ID}}", appId);
    }

    private Result run(String script) {
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
                return Result.failure("timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int code = process.exitValue();
            if (code != 0) {
                return Result.failure("exit " + code + (output.isEmpty() ? "" : ": " + output));
            }
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.toString());
        }
    }

    private record Result(boolean ok, String summary) {
        static Result success() {
            return new Result(true, "ok");
        }

        static Result failure(String summary) {
            return new Result(false, summary);
        }
    }

    private static final String REGISTER_BLOCK = """
            $reg = 'HKCU:\\Software\\Classes\\AppUserModelId\\{{APP_ID}}'
            New-Item -Path $reg -Force | Out-Null
            New-ItemProperty -Path $reg -Name DisplayName -Value 'Commerce Error Radar' -PropertyType String -Force | Out-Null
            $notif = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\{{APP_ID}}'
            New-Item -Path $notif -Force | Out-Null
            New-ItemProperty -Path $notif -Name Enabled -Value 1 -PropertyType DWord -Force | Out-Null
            New-ItemProperty -Path $notif -Name ShowInActionCenter -Value 1 -PropertyType DWord -Force | Out-Null
            """;

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            {{REGISTER}}
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
            $doc = New-Object Windows.Data.Xml.Dom.XmlDocument
            $doc.LoadXml(@'
            {{XML}}
            '@)
            $toast = [Windows.UI.Notifications.ToastNotification]::new($doc)
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('{{APP_ID}}').Show($toast)
            """;
}
