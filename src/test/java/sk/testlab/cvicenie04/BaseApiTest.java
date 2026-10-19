package sk.testlab.cvicenie04;

import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import sk.testlab.cvicenie04.api.*;
import sk.testlab.cvicenie04.config.TestConfig;
import sk.testlab.cvicenie04.model.FixtureAccount;
import sk.testlab.cvicenie04.support.*;

/** Pred každou @Test metódou vlastné dáta a token, po nej upratanie vrátane zlyhaní. */
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class BaseApiTest {
    protected TestConfig config;
    protected ShopApi api;
    protected Artifacts artifacts;
    protected FixtureAccount account;
    protected String token;
    private FixtureManager fixtures;
    private Throwable primaryFailure;

    @BeforeEach
    void prepare(TestInfo info) throws Exception {
        try {
            String method = info.getTestMethod().orElseThrow().getName();
            Secrets secrets = new Secrets();
            artifacts = new Artifacts(method, secrets);
            config = TestConfig.load();
            secrets.add(config.testKey());
            artifacts.put("base.url", config.baseUrl);
            artifacts.put("fixtures.mode", config.mode);
            api = new ShopApi(config, artifacts);
            fixtures = new FixtureManager(config, api, artifacts, secrets);
            account = fixtures.acquire(method);
            token = fixtures.login(account);
        } catch (Exception | AssertionError error) {
            primaryFailure=error;
            if (artifacts != null) artifacts.failure("setup-failure", error);
            throw error;
        }
    }
    /** Zachovať pôvodný neúspech a zapísať ho ešte pred uprataním. */
    protected void runCase(CheckedCase body) throws Exception {
        try { body.run(); artifacts.put("assertions", "PASSED"); }
        catch (Exception | AssertionError error) {
            primaryFailure=error; artifacts.failure("failure", error); throw error;
        }
    }
    @AfterEach
    void cleanup() throws Exception {
        Throwable cleanupFailure = null;
        try { if (fixtures != null) fixtures.close(); }
        catch (RuntimeException | AssertionError error) {
            cleanupFailure=error;
            if (artifacts != null) artifacts.failure("cleanup-failure", error);
        }
        if (artifacts != null) {
            try {
                artifacts.put("finished.utc", Instant.now());
                artifacts.put("result", primaryFailure != null ? "FAILED" : cleanupFailure != null ? "CLEANUP_FAILED" : "PASSED");
            } catch (Exception error) {
                if (cleanupFailure == null) cleanupFailure=error; else cleanupFailure.addSuppressed(error);
            }
        }
        if (cleanupFailure != null) {
            if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
            else throw new AssertionError("Test prešiel, ale upratanie zlyhalo.", cleanupFailure);
        }
    }
    @FunctionalInterface protected interface CheckedCase { void run() throws Exception; }
}
