package pe.bcp.wallet;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Pruebas end-to-end contra el sandbox real del mock
 * (https://bcp-wallet-mock.yefri.workers.dev/groups/1). No se usan mocks de
 * HTTP: cada test genera sus propias claves de idempotencia/datos para no
 * interferir entre corridas.
 */
@QuarkusTest
public class PaymentResourceTest {

    @Test
    public void lowValuePaymentSucceeds() {
        String key = "test-" + UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body("""
                {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":1500,"scenario":"APPROVED"}
                """)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(201)
            .body("status", equalTo("SUCCEEDED"))
            .body("walletId", equalTo("wallet-demo"))
            .body("recipient", equalTo("contact-demo"))
            .body("amountCents", equalTo(1500))
            .body("duplicate", equalTo(false))
            .body("paymentId", notNullValue());
    }

    @Test
    public void amountAboveLowValueLimitIsRejectedBeforeCallingMock() {
        String key = "test-" + UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body("""
                {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":999999,"scenario":"APPROVED"}
                """)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(400)
            .body("code", equalTo("LOW_VALUE_LIMIT_EXCEEDED"));
    }

    @Test
    public void idempotencyReplayWithSameBodyReturnsSamePayment() {
        String key = "test-" + UUID.randomUUID();
        String body = """
            {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":1200,"scenario":"APPROVED"}
            """;

        String firstPaymentId =
            given().contentType(ContentType.JSON).header("Idempotency-Key", key).body(body)
            .when().post("/api/v1/payments")
            .then().statusCode(201).extract().path("paymentId");

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body(body)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(200)
            .body("duplicate", equalTo(true))
            .body("paymentId", equalTo(firstPaymentId));
    }

    @Test
    public void idempotencyConflictWithDifferentBodyReturns409() {
        String key = "test-" + UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body("""
                {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":1000,"scenario":"APPROVED"}
                """)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", key)
            .body("""
                {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":3000,"scenario":"APPROVED"}
                """)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(409)
            .body("code", equalTo("IDEMPOTENCY_CONFLICT"))
            .body("retryable", equalTo(false))
            .body("traceId", notNullValue());
    }

    @Test
    public void reversalOfSucceededPaymentIsAccepted() {
        String key = "test-" + UUID.randomUUID();
        String paymentId =
            given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body("""
                    {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":1800,"scenario":"APPROVED"}
                    """)
            .when()
                .post("/api/v1/payments")
            .then()
                .statusCode(201)
                .body("status", equalTo("SUCCEEDED"))
                .extract().path("paymentId");

        given()
        .when()
            .post("/api/v1/payments/{id}/reversals", paymentId)
        .then()
            .statusCode(200)
            .body("paymentId", equalTo(paymentId))
            .body("status", anyOf(equalTo("PENDING"), equalTo("SUCCEEDED")));
    }

    @Test
    public void qrCreateAndResolveFlowWorks() {
        String qrPayload =
            given()
                .contentType(ContentType.JSON)
                .body("{\"amountCents\":2200,\"expiresInSeconds\":300}")
            .when()
                .post("/api/v1/merchants/merchant-demo/qr")
            .then()
                .statusCode(201)
                .body("amountCents", equalTo(2200))
                .body("payload", startsWith("qrmock:"))
                .extract().path("payload");

        given()
            .contentType(ContentType.JSON)
            .body("{\"payload\":\"" + qrPayload + "\"}")
        .when()
            .post("/api/v1/qr/resolve")
        .then()
            .statusCode(200)
            .body("merchantId", equalTo("merchant-demo"))
            .body("amountCents", equalTo(2200));
    }

    @Test
    public void errorResponseDoesNotLeakInternalDetails() {
        given()
        .when()
            .get("/api/v1/payments/{id}", "pay_does_not_exist_" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("code", equalTo("NOT_FOUND"))
            .body("traceId", notNullValue())
            .body("$", not(hasKey("stackTrace")))
            .body("$", not(hasKey("cause")))
            .body("message", not(containsString("Exception")))
            .body("message", not(containsString("pe.bcp.wallet")))
            .body("message", not(containsString("java.lang")));
    }

    @Test
    public void walletEndpointReturnsNormalizedCamelCaseFields() {
        given()
        .when()
            .get("/api/v1/wallets/wallet-demo")
        .then()
            .statusCode(200)
            .body("walletId", equalTo("wallet-demo"))
            .body("currency", equalTo("PEN"))
            .body("$", not(hasKey("wallet_id")));
    }
}
