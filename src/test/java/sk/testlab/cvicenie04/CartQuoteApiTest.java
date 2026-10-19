package sk.testlab.cvicenie04;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import sk.testlab.cvicenie04.support.Json;
import static org.hamcrest.Matchers.*;

/** Tri POVINNÉ testy zo snímky, žiadny prehliadač ani SQL klient. */
@Tag("api")
@DisplayName("Cvičenie 04 – cenová ponuka a dve odmietnutia")
public class CartQuoteApiTest extends BaseApiTest {
    @Test
    @DisplayName("KB-001 × 2 + MS-001 × 1, STUDENT10, kuriér → 8563 centov")
    void referenceQuote() throws Exception {
        runCase(() -> {
            // ARRANGE: @BeforeEach vytvoril/pridelil fixture a získal jej bearer token.
            artifacts.step("Overenie východiskových cien a skladu cez GET /products");
            Response before = api.products(token);
            before.then().statusCode(200).contentType(ContentType.JSON)
                .body("data.products.find { it.sku == 'KB-001' }.price_cents", equalTo(3490))
                .body("data.products.find { it.sku == 'MS-001' }.price_cents", equalTo(1990))
                .body("data.products.find { it.sku == 'KB-001' }.stock", greaterThanOrEqualTo(2))
                .body("data.products.find { it.sku == 'MS-001' }.stock", greaterThanOrEqualTo(1));
            int keyboardsBefore = before.jsonPath().getInt("data.products.find { it.sku == 'KB-001' }.stock");
            int miceBefore = before.jsonPath().getInt("data.products.find { it.sku == 'MS-001' }.stock");

            // ACT: route je query parameter PHP routera, nie samostatná cesta na serveri.
            artifacts.step("POST /cart/quote: referenčná ponuka");
            String quoteJson = Json.resource("reference-quote.json");
            Response quote = api.request("/cart/quote")
                .auth().oauth2(token)
                .body(quoteJson)
            .when()
                .post(api.url());

            // ASSERT: konštanty sú zo špecifikácie; neprepočítavame očakávania z odpovede.
            quote.then().statusCode(200).contentType(ContentType.JSON)
                .body("data.currency", equalTo("EUR"))
                .body("data.subtotal_cents", equalTo(8970))
                .body("data.discount_cents", equalTo(897))
                .body("data.shipping_cents", equalTo(490))
                .body("data.total_cents", equalTo(8563))
                .body("data.coupon_code", equalTo("STUDENT10"))
                .body("data.delivery_method", equalTo("courier"))
                .body("data.items", hasSize(2))
                .body("data.items.sku", containsInAnyOrder("KB-001", "MS-001"))
                .body("data.items.find { it.sku == 'KB-001' }.quantity", equalTo(2))
                .body("data.items.find { it.sku == 'KB-001' }.unit_price_cents", equalTo(3490))
                .body("data.items.find { it.sku == 'KB-001' }.line_total_cents", equalTo(6980))
                .body("data.items.find { it.sku == 'MS-001' }.quantity", equalTo(1))
                .body("data.items.find { it.sku == 'MS-001' }.unit_price_cents", equalTo(1990))
                .body("data.items.find { it.sku == 'MS-001' }.line_total_cents", equalTo(1990));
            artifacts.put("observed.total.cents", quote.jsonPath().getInt("data.total_cents"));
            artifacts.put("expected.total.cents", 8563);

            // Ponuka podľa kontraktu nerezervuje zásoby. Toto je API kontrola, nie dôkaz z DB.
            artifacts.step("Ponuka nesmie znížiť zásoby");
            api.products(token).then().statusCode(200).contentType(ContentType.JSON)
                .body("data.products.find { it.sku == 'KB-001' }.stock", equalTo(keyboardsBefore))
                .body("data.products.find { it.sku == 'MS-001' }.stock", equalTo(miceBefore));
        });
    }

    @Test
    @DisplayName("quantity ako reťazec \"2\" → HTTP 422 / VALIDATION_ERROR")
    void rejectTextQuantity() throws Exception {
        runCase(() -> {
            artifacts.step("POST /cart/quote: množstvo je JSON reťazec, nie číslo");
            // Súbor obsahuje doslova "quantity": "2". Automatická konverzia na int by zničila test.
            api.request("/cart/quote")
                .auth().oauth2(token)
                .body(Json.resource("invalid-quantity.json"))
            .when()
                .post(api.url())
            .then()
                .statusCode(422).contentType(ContentType.JSON)
                .body("error.code", equalTo("VALIDATION_ERROR"))
                .body("error.details.quantity", not(isEmptyOrNullString()))
                .body("error.request_id", not(isEmptyOrNullString()))
                .body("data", nullValue());
        });
    }

    @Test
    @DisplayName("HUB-001 × 1 pri sklade 0 → HTTP 409 / OUT_OF_STOCK")
    void rejectOutOfStock() throws Exception {
        runCase(() -> {
            artifacts.step("Overenie, že HUB-001 je skutočne vypredaný");
            api.products(token).then().statusCode(200).contentType(ContentType.JSON)
                .body("data.products.find { it.sku == 'HUB-001' }.stock", equalTo(0));
            artifacts.step("POST /cart/quote: odmietnutie vypredaného produktu");
            api.request("/cart/quote")
                .auth().oauth2(token)
                .body(Json.resource("out-of-stock.json"))
            .when()
                .post(api.url())
            .then()
                .statusCode(409).contentType(ContentType.JSON)
                .body("error.code", equalTo("OUT_OF_STOCK"))
                .body("error.details.sku", equalTo("HUB-001"))
                .body("error.details.available", equalTo(0))
                .body("error.request_id", not(isEmptyOrNullString()))
                .body("data", nullValue());
        });
    }
}
