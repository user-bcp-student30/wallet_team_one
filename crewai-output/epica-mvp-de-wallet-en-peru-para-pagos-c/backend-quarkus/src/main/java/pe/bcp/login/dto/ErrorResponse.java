// ErrorResponse.java
package pe.bcp.login.dto;

/**
 * Formato único de error del BFF: code, message, retryable, traceId.
 */
public record ErrorResponse(
        String code,
        String message,
        boolean retryable,
        String traceId) {
}