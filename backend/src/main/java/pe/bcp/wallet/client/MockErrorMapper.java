package pe.bcp.wallet.client;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import pe.bcp.wallet.dto.mock.MockError;
import pe.bcp.wallet.exception.MockApiException;

/**
 * Traduce cualquier respuesta 4xx/5xx del mock externo en una
 * {@link MockApiException} con el código de error original, para que la capa
 * de servicio/los mappers de excepción la normalicen al contrato del BFF.
 */
public class MockErrorMapper implements ResponseExceptionMapper<MockApiException> {

    @Override
    public MockApiException toThrowable(Response response) {
        if (response.getStatus() < 400) {
            return null;
        }
        String code = "UPSTREAM_ERROR";
        try {
            MockError err = response.readEntity(MockError.class);
            if (err != null && err.error != null && !err.error.isBlank()) {
                code = err.error;
            }
        } catch (Exception ignored) {
            // cuerpo no parseable: se usa el código genérico
        }
        return new MockApiException(response.getStatus(), code, code);
    }
}
