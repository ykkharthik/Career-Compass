package web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless HTTP plumbing shared by every page controller: cookie/query/form
 * parsing and writing responses. Holds no state of its own — every method
 * either reads the {@link HttpExchange} it's given or is a pure function —
 * so it's shared across {@link WebServer} and every per-role page class
 * without needing an instance.
 */
public final class Http {

    private Http() {}

    public static String cookie(HttpExchange ex) {
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies)
            for (String part : header.split(";"))
                if (part.trim().startsWith("cc_session="))
                    return part.trim().substring("cc_session=".length());
        return null;
    }

    public static Map<String, String> q(HttpExchange ex) {
        return parseUrlEncoded(ex.getRequestURI().getRawQuery());
    }

    public static Map<String, String> form(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseUrlEncoded(body);
    }

    /**
     * Like {@link #form}, but keeps every value for a repeated field name —
     * needed for a checkbox group (e.g. {@code <input name="emails" ...>}
     * repeated per row) where {@code form()}'s single-value map would silently
     * keep only the last one.
     */
    public static Map<String, List<String>> formMulti(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (body.isBlank()) return map;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    private static Map<String, String> parseUrlEncoded(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    public static void html(HttpExchange ex, int status, String page) throws IOException {
        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static void redirect(HttpExchange ex, String to) throws IOException {
        ex.getResponseHeaders().add("Location", to);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }
}
