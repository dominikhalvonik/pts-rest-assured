package sk.testlab.cvicenie04.api;

import io.restassured.response.Response;
import java.time.Instant;
import java.util.*;
import sk.testlab.cvicenie04.config.TestConfig;
import sk.testlab.cvicenie04.model.FixtureAccount;
import sk.testlab.cvicenie04.support.*;

/** Vlastníctvo mazania sa odvodzuje iba z odpovede na create v TOMTO behu, nie z konfigurácie. */
public final class FixtureManager implements AutoCloseable {
    private final TestConfig config;
    private final ShopApi api;
    private final Artifacts artifacts;
    private final Secrets secrets;
    private final Set<String> createdHere = new LinkedHashSet<>();
    private final Set<String> retainedForGui = new HashSet<>();
    private final List<String> issuedTokens = new ArrayList<>();
    private boolean closed;

    public FixtureManager(TestConfig config, ShopApi api, Artifacts artifacts, Secrets secrets) {
        this.config=config; this.api=api; this.artifacts=artifacts; this.secrets=secrets;
        secrets.add(config.testKey());
    }
    public FixtureAccount acquire(String testMethod) {
        if (!config.automatic()) {
            FixtureAccount account = config.manualAccount(testMethod);
            secrets.add(account.password());
            artifacts.note("workspace.source", "MANUALLY_ASSIGNED_NEVER_DELETED_BY_THIS_TEST");
            artifacts.note("workspace.code", account.workspace());
            return account;
        }
        artifacts.step("Vytvorenie vlastného izolovaného run-* priestoru");
        Response response;
        try {
            response = api.request("/testing/fixtures").header("X-Test-Key", config.testKey())
                .body("{}").when().post(api.url());
        } catch (RuntimeException error) {
            artifacts.note("fixture.create", "OUTCOME_UNKNOWN_NO_RETRY_MANUAL_REVIEW");
            throw new IllegalStateException("Vytvorenie fixture nemá známu odpoveď. Server mohol priestor vytvoriť. "
                + "Požiadavku automaticky neopakujeme; skontroluj serverové logy.");
        }
        ShopApi.requireJsonStatus(response, 201, "POST /testing/fixtures");
        String workspace;
        try { workspace = response.jsonPath().getString("data.workspace_code"); }
        catch (RuntimeException error) { throw sensitiveError("Fixture odpoveď nemožno načítať; skontroluj server. Neznámy priestor sa nemaže."); }
        if (!FixtureAccount.isRunWorkspace(workspace))
            throw sensitiveError("Fixture nevrátila pôvodný run-* identifikátor; neznámy priestor sa nemaže.");
        createdHere.add(workspace); // Ihneď: aj pri chybnom hesle alebo emaile vieme upratať vlastný priestor.
        artifacts.note("workspace.source", "CREATED_BY_THIS_TEST");
        artifacts.note("workspace.code", workspace);
        try {
            String email = response.jsonPath().getString("data.email");
            String password = response.jsonPath().getString("data.password");
            secrets.add(password);
            return new FixtureAccount(workspace, email, password);
        } catch (RuntimeException error) { throw sensitiveError("Fixture neobsahuje použiteľné prihlasovacie údaje."); }
    }
    public String login(FixtureAccount account) {
        artifacts.step("Získanie bearer tokenu vlastného fixture účtu");
        secrets.add(account.password());
        Response response = api.request("/auth/token")
            .body(Json.encode(Map.of("email", account.email(), "password", account.password())))
            .when().post(api.url());
        ShopApi.requireJsonStatus(response, 201, "POST /auth/token");
        String token;
        try {
            Object value = response.jsonPath().get("data.token");
            if (!(value instanceof String s) || !s.matches("[a-f0-9]{64}"))
                throw sensitiveError("Nedostal som platný bearer token.");
            token = s;
            secrets.add(token); issuedTokens.add(token); // Evidovať pred kontrolou ďalších polí.
            if (!"Bearer".equals(response.jsonPath().getString("data.token_type")))
                throw sensitiveError("Neplatný typ tokenu.");
            Instant expiry = Instant.parse(response.jsonPath().getString("data.expires_at"));
            if (!expiry.isAfter(Instant.now())) throw sensitiveError("Token už nie je platný; over čas servera/PC.");
        } catch (RuntimeException error) { throw sensitiveError("Neplatná odpoveď /auth/token. Citlivé telo ani pôvodná parsovacia výnimka sa nelogujú."); }
        verifyIdentity(token, account);
        return token;
    }
    private void verifyIdentity(String token, FixtureAccount account) {
        Response response = api.request("/auth/me").auth().oauth2(token).when().get(api.url());
        ShopApi.requireJsonStatus(response, 200, "GET /auth/me");
        try {
            String workspace = response.jsonPath().getString("data.user.workspace_code");
            String email = response.jsonPath().getString("data.user.email");
            if (!account.workspace().equals(workspace) || !account.email().equalsIgnoreCase(email))
                throw sensitiveError("Token nepatrí pridelenému účtu a priestoru.");
        } catch (RuntimeException error) { throw sensitiveError("Identita z /auth/me nezodpovedá fixture účtu."); }
        artifacts.note("workspace.identity.verified", true);
    }
    public void retainForGui(FixtureAccount account) {
        if (!createdHere.contains(account.workspace())) throw new IllegalStateException("Uchovať možno iba priestor vytvorený týmto behom.");
        retainedForGui.add(account.workspace());
    }
    @Override public void close() {
        if (closed) return;
        closed=true;
        RuntimeException failure = null;
        for (String token : issuedTokens) {
            try {
                Response response = api.request("/auth/logout").auth().oauth2(token).body("{}").when().post(api.url());
                ShopApi.requireJsonStatus(response, 200, "POST /auth/logout");
                try {
                    if (!Boolean.TRUE.equals(response.jsonPath().get("data.logged_out")))
                        throw sensitiveError("Odhlásenie nepotvrdilo zrušenie vlastného tokenu.");
                } catch (RuntimeException parsingError) { throw sensitiveError("Odhlásenie nevrátilo očakávaný JSON s logged_out=true."); }
            } catch (RuntimeException error) { failure = combine(failure, error); }
        }
        for (String code : createdHere) {
            if (retainedForGui.contains(code)) { artifacts.note("cleanup.retained.for.gui", code); continue; }
            try {
                // Hodnoty manual.* ani ľubovoľný parameter sem nevedú; žiadne mazanie demo.
                if (!FixtureAccount.isRunWorkspace(code)) throw new IllegalStateException("Odmietnutý neplatný identifikátor pracovného priestoru.");
                Response response = api.request("/testing/fixtures/" + code).header("X-Test-Key", config.testKey())
                    .when().delete(api.url());
                ShopApi.requireJsonStatus(response, 200, "DELETE vlastnej fixture");
                if (!code.equals(response.jsonPath().getString("data.workspace_code"))
                        || !Boolean.TRUE.equals(response.jsonPath().get("data.deleted")))
                    throw new IllegalStateException("Server nepotvrdil odstránenie presne vlastného priestoru.");
                artifacts.note("cleanup.deleted.workspace", code);
            } catch (RuntimeException error) { failure = combine(failure, error); }
        }
        artifacts.note("cleanup.mode", config.automatic() ? "OWN_CREATED_WORKSPACES_ONLY" : "OWN_TOKEN_ONLY_MANUAL_WORKSPACES_PRESERVED");
        if (failure != null) throw failure;
    }
    private static RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        first.addSuppressed(next); return first;
    }
    private static IllegalStateException sensitiveError(String message) { return new IllegalStateException(message); }
}
