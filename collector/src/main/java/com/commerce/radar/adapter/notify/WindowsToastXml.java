package com.commerce.radar.adapter.notify;

/**
 * ToastGeneric XML for the Windows Runtime notifier.
 */
public final class WindowsToastXml {

    private WindowsToastXml() {
    }

    public static String document(String title, String body) {
        return """
                <toast>
                  <visual>
                    <binding template="ToastGeneric">
                      <text>%s</text>
                      <text>%s</text>
                    </binding>
                  </visual>
                </toast>
                """.formatted(escape(title), escape(body)).strip();
    }

    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
