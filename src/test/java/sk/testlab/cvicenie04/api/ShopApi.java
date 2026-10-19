package sk.testlab.cvicenie04.api;

import io.restassured.config.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import sk.testlab.cvicenie04.config.TestConfig;
import sk.testlab.cvicenie04.support.Artifacts;
import static io.restassured.RestAssured.given;

/** Tenká konfigurácia spojenia; doménové assertions sú v CartQuoteApiTest. */
public final class ShopApi {
    private final TestConfig config;
    private final Artifacts artifacts;
    private final RestAssuredConfig restConfig;
    @SuppressWarnings("deprecation")
    public ShopApi(TestConfig config, Artifacts artifacts) {
        this.config = config; this.artifacts = artifacts;
        int timeout = Math.toIntExact(config.timeout.toMillis());
        restConfig = RestAssuredConfig.config()
            .redirect(RedirectConfig.redirectConfig().followRedirects(false))
            .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"))
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeout)
                .setParam("http.socket.timeout", timeout)
                .setParam("http.connection-manager.timeout", (long)timeout)
                .httpClientFactory(() -> {
                    // REST Assured 5 používa Apache HttpClient 4. Tento konkrétny klient vypne retry.
                    DefaultHttpClient client = new DefaultHttpClient();
                    client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
                    return client;
                }));
    }
    public String url() { return config.apiUrl; }
    public RequestSpecification request(String route) {
        if (route == null || !route.matches("/[A-Za-z0-9_/-]+"))
            throw new IllegalArgumentException("Neplatná interná route.");
        // Vždy nová špecifikácia, žiadna spoločná session, cookie filter ani statický bearer token.
        return given().config(restConfig).contentType(ContentType.JSON).accept(ContentType.JSON)
            .queryParam("route", route).filter(artifacts.filter(route));
    }
    public Response products(String token) {
        return request("/products").auth().oauth2(token).when().get(url());
    }
    public static void requireJsonStatus(Response response, int expected, String operation) {
        if (response.statusCode() != expected) {
            String hint = switch (response.statusCode()) {
                case 404 -> " Skontroluj base.url a zapnutie testovacieho API v PHP (pri fixtures).";
                case 403 -> " Skontroluj X-Test-Key oproti súkromnej PHP konfigurácii.";
                case 401 -> " Skontroluj prihlasovacie údaje alebo platnosť tokenu.";
                case 429 -> " Server obmedzil pokusy o prihlásenie; nepridávaj nekonečné retry.";
                case 503 -> " Skontroluj MariaDB, pdo_mysql, konfiguráciu a inštaláciu backendu.";
                default -> " Pri presmerovaní nastav priamo konečnú adresu backendu.";
            };
            throw new IllegalStateException(operation + ": očakávané HTTP " + expected
                + ", prijaté " + response.statusCode() + "." + hint);
        }
        String type = response.contentType();
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
            throw new IllegalStateException(operation + ": odpoveď nemá Content-Type application/json.");
    }
}
