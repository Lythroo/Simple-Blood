package com.simpleblood.surface.store;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class StainWorldKey {

    static final int DEFAULT_PORT = 25565;
    private static final int MAX_READABLE = 40;

    private StainWorldKey() {}

    public static String singleplayer(String saveFolder) {
        String name = saveFolder == null ? "" : saveFolder.strip();
        return "world-" + safe(name);
    }

    public static String server(String address) {
        return "server-" + safe(normaliseAddress(address));
    }

    public static String dimension(String id) {
        return safe(id == null ? "" : id.strip().toLowerCase(Locale.ROOT));
    }

    static String normaliseAddress(String address) {
        if (address == null) return "";
        String a = address.strip().toLowerCase(Locale.ROOT);
        String host = a;
        String port = null;
        if (a.startsWith("[")) {
            int close = a.indexOf(']');
            if (close > 0) {
                host = a.substring(1, close);
                if (a.length() > close + 1 && a.charAt(close + 1) == ':') port = a.substring(close + 2);
            }
        } else {
            int colon = a.lastIndexOf(':');
            if (colon >= 0 && a.indexOf(':') == colon) {
                host = a.substring(0, colon);
                port = a.substring(colon + 1);
            }
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (port != null && (port.isEmpty() || port.equals(Integer.toString(DEFAULT_PORT)))) port = null;
        return port == null ? host : host + ":" + port;
    }

    static String safe(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_READABLE; i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || (c == '.' && sb.length() > 0);
            sb.append(ok ? c : '_');
        }
        if (sb.length() == 0) sb.append('_');
        return sb + "-" + String.format(Locale.ROOT, "%08x", fnv1a(raw));
    }

    private static int fnv1a(String s) {
        int h = 0x811C9DC5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x01000193;
        }
        return h;
    }
}
