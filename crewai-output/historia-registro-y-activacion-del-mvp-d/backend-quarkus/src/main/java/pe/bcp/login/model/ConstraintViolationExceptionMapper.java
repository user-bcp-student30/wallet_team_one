// ConstraintViolationExceptionMapper.java
package pe.bcp.login.resource;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.bcp.login.dto.ErrorResponse;
import pe.bcp.login.dto.ErrorResponse.FieldError;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fields.add(new FieldError(lastNode(violation.getPropertyPath()), violation.getMessage()));
        }
        String correlationId = CorrelationIds.resolve(headers.getHeaderString(CorrelationIds.HEADER));
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR",
                "Hay datos inválidos en la solicitud. Corrígelos e inténtalo de nuevo.",
                correlationId,
                fields);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .header(CorrelationIds.HEADER, correlationId)
                .entity(body)
                .build();
    }

    private static String lastNode(Path path) {
        String name = "request";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }
}