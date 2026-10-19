package sk.testlab.cvicenie04.tools;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import sk.testlab.cvicenie04.api.*;
import sk.testlab.cvicenie04.config.TestConfig;
import sk.testlab.cvicenie04.model.FixtureAccount;
import sk.testlab.cvicenie04.support.*;

/** Voliteľný nástroj, NIE štvrtý povinný test. Spúšťa sa len explicitne. */
public class GuiFixtureTool {
    @Test
    void prepareOneGuiWorkspace() throws Exception {
        if (!Boolean.getBoolean("prepare.gui")) throw new IllegalStateException(
            "Nástroj úmyselne PONECHÁ vlastný priestor. Spusti s -Dprepare.gui=true.");
        TestConfig config = TestConfig.load();
        if (!config.automatic()) throw new IllegalStateException("Nástroj vyžaduje fixtures.mode=api a dôveryhodný X-Test-Key.");
        Secrets secrets = new Secrets(); secrets.add(config.testKey());
        Artifacts artifacts = new Artifacts("GuiFixtureTool", secrets);
        ShopApi api = new ShopApi(config, artifacts);
        try (FixtureManager manager = new FixtureManager(config, api, artifacts, secrets)) {
            FixtureAccount account = manager.acquire("prepareGui");
            String token = manager.login(account);
            ShopApi.requireJsonStatus(api.products(token), 200, "GET /products");
            Path directory = Path.of("target", "prepared-gui", account.workspace());
            Files.createDirectories(directory);
            Path output = directory.resolve("test.properties");
            try {
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) { /* Windows: prístup riadia dedené ACL. */ }
            Properties properties = new Properties();
            properties.setProperty("base.url", config.baseUrl);
            properties.setProperty("test.email", account.email());
            properties.setProperty("test.password", account.password());
            properties.setProperty("workspace.code", account.workspace());
            properties.setProperty("browser", "chrome"); properties.setProperty("headless", "false");
            properties.setProperty("timeout.seconds", "30"); properties.setProperty("cleanup.order", "true");
            properties.setProperty("app.version", "1.0.0");
            // Jediný ÚMYSELNE citlivý výstup: lokálny config pre GUI klienta, nie HTTP log.
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                properties.store(writer, "PRIVATE: generated GUI fixture credentials. Do not commit or publish.");
            }
            try { Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { /* Windows ACL */ }
            manager.retainForGui(account); // Len tento vlastný priestor, token sa pri close stále zruší.
            artifacts.put("prepared.workspace", account.workspace());
            System.out.println("Lokálny GUI config (obsahuje heslo): " + output.toAbsolutePath());
            System.out.println("Priestor zostáva pre GUI: " + account.workspace());
            System.out.println("Po skončení ho vyučujúci odstráni v PHP projekte: php bin/fixtures.php delete " + account.workspace());
        } catch (Exception | AssertionError error) {
            artifacts.failure("tool-failure", error); throw error;
        }
    }
}
