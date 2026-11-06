// archivo: auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java
package com.bcp.workshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Propuesta de pruebas de contrato HTTP.
 *
 * Requiere AUTH_BASE_URL apuntando a un servicio de pruebas ya iniciado.
 * Confirmar el formato JSON con Backend antes de integrar esta prueba.
 */
class AuthResourceTest {
    private static final String INJECTION = "' OR '1'='1";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void loginValidoDevuelveUsernameYToken() throws Exception {
        HttpResponse<String> response = login("student", "Workshop123!");

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(
                Pattern.compile("\"user\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"student\"")
                        .matcher(response.body())
                        .find(),
                "La respuesta debe incluir user.username igual a student");
        assertTrue(
                Pattern.compile("\"token\"\\s*:")
                        .matcher(response.body())
                        .find(),
                "La respuesta debe incluir token");
    }

    @Test
    void intentoDeInyeccionEnUsuarioDevuelve401SinToken() throws Exception {
        HttpResponse<String> response = login(INJECTION, "Workshop123!");

        assertEquals(401, response.statusCode(), response.body());
        assertFalse(
                Pattern.compile("\"token\"\\s*:")
                        .matcher(response.body())
                        .find(),
                "Una respuesta 401 no debe incluir token");
    }

    @Test
    void intentoDeInyeccionEnContrasenaDevuelve401SinToken() throws Exception {
        HttpResponse<String> response = login("student", INJECTION);

        assertEquals(401, response.statusCode(), response.body());
        assertFalse(
                Pattern.compile("\"token\"\\s*:")
                        .matcher(response.body())
                        .find(),
                "Una respuesta 401 no debe incluir token");
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String baseUrl = System.getenv("AUTH_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Defina AUTH_BASE_URL con la URL base del servicio de pruebas");
        }

        String url = baseUrl.replaceAll("/+$", "") + "/auth/login";
        String body = "{\"username\":\"" + escapeJson(username)
                + "\",\"password\":\"" + escapeJson(password) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}