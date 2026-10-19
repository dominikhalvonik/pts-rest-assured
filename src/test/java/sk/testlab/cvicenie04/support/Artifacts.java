package sk.testlab.cvicenie04.support;

import io.restassured.filter.Filter;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Loguje HTTP špecifikáciu (nie surový packet capture), vždy po odstránení citlivých údajov. */
public final class Artifacts {
    private final Path directory;
    private final Secrets secrets;
    private final AtomicInteger counter = new AtomicInteger();
    private final Properties metadata = new Properties();
    public Artifacts(String name, Secrets secrets) throws IOException {
        this.secrets = secrets;
        directory = Path.of("target", "artifacts", name.replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + UUID.randomUUID());
        Files.createDirectories(directory);
        put("test", name); put("started.utc", Instant.now());
        System.out.println("Dôkazy: " + directory.toAbsolutePath());
    }
    public void put(String key, Object value) throws IOException {
        metadata.setProperty(key, secrets.redact(String.valueOf(value)));
        try (Writer w = Files.newBufferedWriter(directory.resolve("run.properties"), StandardCharsets.UTF_8)) {
            metadata.store(w, "Cvicenie 04 - API");
        }
    }
    public void step(String description) {
        try { put("step", description); }
        catch (IOException error) { throw new UncheckedIOException("Nemožno zaznamenať krok testu.", error); }
        System.out.println("[KROK] " + description);
    }
    public void note(String key, Object value) {
        try { put(key, value); }
        catch (IOException error) { System.err.println("Nepodarilo sa zapísať metadáta: " + key); }
    }
    public void write(String name, String text) throws IOException {
        Files.writeString(directory.resolve(name), secrets.redact(text), StandardCharsets.UTF_8);
    }
    public void failure(String phase, Throwable failure) {
        StringWriter text = new StringWriter(); failure.printStackTrace(new PrintWriter(text));
        try { write(phase + ".txt", text.toString()); }
        catch (IOException error) { failure.addSuppressed(error); }
        note("failure.phase", phase);
    }
    public Filter filter(String route) {
        return (request, responseSpec, context) -> {
            int number = counter.incrementAndGet();
            String prefix = String.format(Locale.ROOT, "%02d", number);
            boolean sensitive = route.startsWith("/auth/") || route.startsWith("/testing/");
            Map<String,Object> log = new LinkedHashMap<>();
            log.put("started_utc", Instant.now().toString());
            log.put("method", request.getMethod());
            log.put("uri", request.getURI());
            log.put("route", route);
            log.put("authorization", request.getHeaders().hasHeaderWithName("Authorization") ? "[REDACTED]" : "absent");
            log.put("test_key", request.getHeaders().hasHeaderWithName("X-Test-Key") ? "[REDACTED]" : "absent");
            log.put("request_body", sensitive ? "[SENSITIVE BODY OMITTED]" : safeBody(request.getBody()));
            safeWrite(prefix + "-request.json", Json.encode(log));
            long start = System.nanoTime();
            try {
                Response response = context.next(request, responseSpec);
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("http_status", response.statusCode());
                result.put("content_type", response.contentType());
                result.put("request_id", response.getHeader("X-Request-Id"));
                result.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000);
                result.put("response_body", sensitive ? "[SENSITIVE BODY OMITTED]" : safeBody(response.asString()));
                safeWrite(prefix + "-response.json", Json.encode(result));
                return response; // Žiadne prepisovanie výsledku alebo statusu.
            } catch (RuntimeException error) {
                // Správa transportnej výnimky môže obsahovať interné detaily; hlavičky netlačíme.
                safeWrite(prefix + "-transport.json", Json.encode(Map.of(
                    "exception_class", error.getClass().getName(), "note", "Bez automatického opakovania požiadavky.")));
                throw error;
            }
        };
    }
    private Object safeBody(Object raw) {
        if (raw == null) return "[NO BODY]";
        if (!(raw instanceof String text)) return "[NON-TEXT BODY OMITTED]";
        if (text.isBlank()) return "[EMPTY BODY]";
        try { return secrets.mask(JsonPath.from(text).get()); }
        catch (RuntimeException error) { return "[INVALID OR NON-JSON BODY OMITTED]"; }
    }
    private void safeWrite(String name, String text) {
        try { write(name, text); }
        catch (IOException e) { System.err.println("Nepodarilo sa zapísať HTTP dôkaz " + name); }
    }
}
