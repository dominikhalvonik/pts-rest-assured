package sk.testlab.cvicenie04.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Malý JSON encoder pre prihlasovacie údaje a bezpečné logy. Parsovanie poskytuje REST Assured. */
public final class Json {
    private Json() {}
    public static String resource(String name) {
        if (!name.matches("[A-Za-z0-9_-]+\\.json")) throw new IllegalArgumentException("Neplatný názov payloadu.");
        try (InputStream input = Json.class.getResourceAsStream("/payloads/" + name)) {
            if (input == null) throw new IllegalStateException("Chýba payload: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException("Payload sa nepodarilo načítať.", e); }
    }
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number n) {
            String s = n.toString();
            if (!s.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
                throw new IllegalArgumentException("Neplatné JSON číslo.");
            return s;
        }
        if (value instanceof Map<?,?> m) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            for (var e : m.entrySet()) {
                if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("JSON kľúč musí byť text.");
                j.add(quote((String)e.getKey()) + ":" + encode(e.getValue()));
            }
            return j.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object item : items) j.add(encode(item));
            return j.toString();
        }
        throw new IllegalArgumentException("Nepodporovaný JSON typ.");
    }
    public static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t"); case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                default -> {
                    if (c < 32 || Character.isSurrogate(c)) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
