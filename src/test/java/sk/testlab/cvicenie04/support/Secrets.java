package sk.testlab.cvicenie04.support;

import java.util.*;

/** Druhá ochrana popri úplnom vynechaní citlivých HTTP tiel a hlavičiek. */
public final class Secrets {
    private final Set<String> secrets = new LinkedHashSet<>();
    private static final Set<String> SENSITIVE = Set.of("password", "token", "csrf_token", "access_token", "authorization", "cookie", "set-cookie", "test_api_key", "x-test-key");
    public synchronized void add(String secret) { if (secret != null && !secret.isBlank()) secrets.add(secret); }
    public synchronized String redact(String text) {
        String result = text;
        // Dlhšie tajomstvo nahradiť skôr, aby sa pri vnorených hodnotách nič neodkrylo.
        List<String> values = new ArrayList<>(secrets);
        values.sort(Comparator.comparingInt(String::length).reversed());
        for (String value : values) {
            result = result.replace(value, "[REDACTED]");
            String escaped = Json.quote(value);
            result = result.replace(escaped.substring(1, escaped.length()-1), "[REDACTED]");
        }
        return result;
    }
    public Object mask(Object value) {
        if (value instanceof Map<?,?> m) {
            Map<String,Object> result = new LinkedHashMap<>();
            for (var entry : m.entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, SENSITIVE.contains(key.toLowerCase(Locale.ROOT)) ? "[REDACTED]" : mask(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Iterable<?> items) {
            List<Object> result = new ArrayList<>();
            for (Object item : items) result.add(mask(item));
            return result;
        }
        if (value instanceof String s) return redact(s);
        return value;
    }
}
