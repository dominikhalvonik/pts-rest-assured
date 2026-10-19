package sk.testlab.cvicenie04.config;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import sk.testlab.cvicenie04.model.FixtureAccount;

/** Bezpečné zlyhanie pri chýbajúcej konfigurácii, nikdy tichý fallback na demo účet. */
public final class TestConfig {
    public static final List<String> CASES = List.of("referenceQuote", "rejectTextQuantity", "rejectOutOfStock");
    public final String baseUrl, apiUrl, mode;
    public final Duration timeout;
    private final String testKey;
    private final Map<String, FixtureAccount> manual;

    private TestConfig(String baseUrl, String mode, Duration timeout, String key, Map<String, FixtureAccount> manual) {
        this.baseUrl = baseUrl;
        this.apiUrl = URI.create(baseUrl).resolve("api/index.php").toASCIIString();
        this.mode = mode;
        this.timeout = timeout;
        this.testKey = key;
        this.manual = Map.copyOf(manual);
    }
    public static TestConfig load() throws IOException {
        Path path = Path.of(System.getProperty("test.config", "config/test.properties"));
        if (!Files.isRegularFile(path)) throw new IllegalStateException(
            "Chýba " + path.toAbsolutePath() + ". Pracovný priečinok musí obsahovať pom.xml a config/.");
        Properties properties = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(r); }
        return parse(properties, System.getProperties(), System.getenv());
    }
    /** Oddelená čistá funkcia na overenie konfigurácie bez servera či REST Assured. */
    public static TestConfig parse(Properties file, Properties system, Map<String, String> env) {
        String mode = value(file, system, env, "fixtures.mode", "FIXTURES_MODE", "api").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("api", "manual").contains(mode)) throw new IllegalArgumentException("fixtures.mode musí byť api alebo manual.");
        String input = value(file, system, env, "base.url", "BASE_URL", "http://127.0.0.1:8080/").trim();
        String base = normalizeBaseUrl(input);
        boolean http = flag(value(file, system, env, "allow.http", "ALLOW_HTTP", "false"), "allow.http");
        if (base.startsWith("http://") && !http)
            throw new IllegalArgumentException("HTTP posiela prihlasovacie údaje bez šifrovania. Pre lokálnu učebňu nastav allow.http=true, inak použi HTTPS.");
        int seconds;
        try { seconds = Integer.parseInt(value(file, system, env, "timeout.seconds", "TIMEOUT_SECONDS", "30").trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("timeout.seconds musí byť celé číslo 1..120."); }
        if (seconds < 1 || seconds > 120) throw new IllegalArgumentException("timeout.seconds musí byť 1..120.");
        Map<String, FixtureAccount> accounts = new LinkedHashMap<>();
        String key = "";
        if (mode.equals("api")) {
            // Tajomstvá nečítame z JVM -D argumentov, aby sa neocitli v procesovom príkaze.
            key = env.containsKey("TEST_API_KEY") ? env.get("TEST_API_KEY") : file.getProperty("test.api.key", "");
            if (key == null || key.trim().startsWith("SEM_") || key.length() < 32 || key.length() > 512
                    || !key.chars().allMatch(c -> c >= 33 && c <= 126))
                throw new IllegalArgumentException("Vyplň test.api.key alebo TEST_API_KEY: skutočný tlačiteľný ASCII kľúč aspoň 32 znakov, zhodný s PHP.");
        } else {
            Set<String> workspaces = new HashSet<>(), emails = new HashSet<>();
            for (String name : CASES) {
                String p = "manual." + name + ".";
                FixtureAccount account = new FixtureAccount(required(file, p + "workspace").trim(),
                    required(file, p + "email").trim(), required(file, p + "password"));
                if (!workspaces.add(account.workspace()) || !emails.add(account.email().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Každý test musí dostať iný workspace aj účet.");
                accounts.put(name, account);
            }
        }
        return new TestConfig(base, mode, Duration.ofSeconds(seconds), key, accounts);
    }
    public static String normalizeBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
                throw new IllegalArgumentException();
            String path = uri.getPath();
            if (path.endsWith("/api/index.php") || path.endsWith("/api/index.php/") || Arrays.stream(path.split("/", -1)).anyMatch(segment -> segment.equals(".") || segment.equals("..")))
                throw new IllegalArgumentException();
            String normal = scheme.toLowerCase(Locale.ROOT) + ":" + value.substring(value.indexOf(':') + 1);
            return normal.endsWith("/") ? normal : normal + "/";
        } catch (IllegalArgumentException error) {
            // Neopakovať problematickú URL: môže obsahovať používateľom omylom vložené heslo.
            throw new IllegalArgumentException("base.url musí byť HTTP(S) adresa WEBU bez prihlasovacích údajov, query či fragmentu; nie api/index.php.");
        }
    }
    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank() || v.trim().startsWith("SEM_")) throw new IllegalArgumentException("Vyplň " + key + ".");
        return v;
    }
    private static String value(Properties p, Properties s, Map<String, String> e, String key, String envKey, String fallback) {
        return s.getProperty(key, e.getOrDefault(envKey, p.getProperty(key, fallback)));
    }
    private static boolean flag(String text, String name) {
        if ("true".equalsIgnoreCase(text.trim())) return true;
        if ("false".equalsIgnoreCase(text.trim())) return false;
        throw new IllegalArgumentException(name + " musí byť true alebo false.");
    }
    public boolean automatic() { return mode.equals("api"); }
    public String testKey() { return testKey; }
    public FixtureAccount manualAccount(String method) {
        FixtureAccount a = manual.get(method);
        if (a == null) throw new IllegalArgumentException("Pre tento test neexistuje ručne pridelený účet.");
        return a;
    }
    @Override public String toString() { return "TestConfig[mode=" + mode + ", secrets=REDACTED]"; }
}
