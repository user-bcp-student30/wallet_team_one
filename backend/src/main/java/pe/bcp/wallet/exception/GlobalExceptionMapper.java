package pe.bcp.wallet.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import pe.bcp.wallet.dto.api.ErrorResponse;

import java.util.UUID;

/**
 * Único punto de traducción de excepciones al formato de error unificado del
 * contrato ({@code code/message/retryable/traceId}). Nunca expone
 * stacktraces, SQL ni nombres de clase internos al cliente.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        String traceId = UUID.randomUUID().toString();

        if (exception instanceof AppException appEx) {
            LOG.warnf("AppException code=%s traceId=%s msg=%s", appEx.getCode(), traceId, appEx.getMessage());
            return build(appEx.getHttpStatus(), appEx.getCode(), appEx.getMessage(), appEx.isRetryable(), traceId);
        }

        if (exception instanceof MockApiException mockEx) {
            LOG.warnf("MockApiException status=%d code=%s traceId=%s",
                    mockEx.getHttpStatus(), mockEx.getMockCode(), traceId);
            return build(mapMockStatus(mockEx), mockEx.getMockCode(),
                    humanMessage(mockEx.getMockCode()), isRetryable(mockEx), traceId);
        }

        if (exception instanceof ConstraintViolationException cve) {
            LOG.warnf("ConstraintViolationException traceId=%s msg=%s", traceId, cve.getMessage());
            return build(400, "VALIDATION_ERROR", "Datos de entrada inválidos.", false, traceId);
        }

        if (exception instanceof NotFoundException) {
            return build(404, "NOT_FOUND", "Recurso no encontrado.", false, traceId);
        }

        // Error no previsto: no se filtran detalles internos (stacktrace, SQL, clases).
        LOG.errorf(exception, "Unhandled exception traceId=%s", traceId);
        return build(500, "INTERNAL_ERROR", "Ocurrió un error interno. Intente nuevamente.", true, traceId);
    }

    private Response build(int status, String code, String message, boolean retryable, String traceId) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(code, message, retryable, traceId))
                .build();
    }

    private int mapMockStatus(MockApiException ex) {
        return switch (ex.getMockCode()) {
            case "QR_EXPIRED" -> 410;
            case "NOT_FOUND", "MERCHANT_NOT_FOUND" -> 404;
            case "GROUP_LIMIT_RESET_REQUIRED" -> 429;
            case "KYC_UNAVAILABLE" -> 503;
            case "IDEMPOTENCY_CONFLICT", "INSUFFICIENT_FUNDS", "WALLET_NOT_ACTIVE",
                 "PAYMENT_NOT_REVERSIBLE", "RECEIPT_NOT_READY", "DECISION_CONFLICT",
                 "PROVIDER_ALREADY_FINAL" -> 409;
            case "INVALID_TTL", "INVALID_QR", "INVALID_DECISION", "INVALID_STATUS",
                 "INVALID_MOCK_LATENCY" -> 400;
            default -> ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 502;
        };
    }

    private boolean isRetryable(MockApiException ex) {
        return switch (ex.getMockCode()) {
            case "KYC_UNAVAILABLE", "GROUP_LIMIT_RESET_REQUIRED", "UPSTREAM_ERROR" -> true;
            default -> ex.getHttpStatus() >= 500;
        };
    }

    private String humanMessage(String mockCode) {
        return switch (mockCode) {
            case "IDEMPOTENCY_CONFLICT" -> "Ya existe una solicitud con la misma clave de idempotencia pero datos distintos.";
            case "INSUFFICIENT_FUNDS" -> "Saldo insuficiente para completar la operación.";
            case "WALLET_NOT_ACTIVE" -> "La wallet no está activa.";
            case "QR_EXPIRED" -> "El código QR ya expiró.";
            case "PAYMENT_NOT_REVERSIBLE" -> "El pago no admite reverso en su estado actual.";
            case "RECEIPT_NOT_READY" -> "El comprobante aún no está disponible.";
            case "NOT_FOUND" -> "Recurso no encontrado.";
            case "MERCHANT_NOT_FOUND" -> "Comercio no encontrado.";
            case "KYC_UNAVAILABLE" -> "El proveedor de verificación no está disponible. Intente nuevamente.";
            case "GROUP_LIMIT_RESET_REQUIRED" -> "El entorno de pruebas alcanzó su límite. Reinicie el sandbox.";
            default -> "No se pudo completar la operación con el proveedor externo.";
        };
    }
}
