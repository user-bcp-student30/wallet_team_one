// ErrorResponse.java
package pe.bcp.login.dto;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}