// PaymentResource.java
package pe.bcp.login.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import pe.bcp.login.dto.CreatePaymentRequest;
import pe.bcp.login.dto.PaymentResponse;
import pe.bcp.login.service.CreatePaymentOutcome;
import pe.bcp.login.service.PaymentService;

/**
 * Capa HTTP de pagos. No contiene lógica de negocio.
 */
@Path("/api/v1/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private final PaymentService paymentService;

    @Inject
    public PaymentResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @POST
    public RestResponse<PaymentResponse> create(
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @NotNull(message = "El cuerpo de la solicitud es obligatorio") @Valid CreatePaymentRequest request) {
        CreatePaymentOutcome outcome = paymentService.createPayment(idempotencyKey, request.toCommand());
        PaymentResponse body = PaymentResponse.from(outcome.payment());
        if (outcome.replayed()) {
            return RestResponse.ok(body);
        }
        return RestResponse.status(Response.Status.CREATED, body);
    }

    @GET
    @Path("/{paymentId}")
    public PaymentResponse get(@PathParam("paymentId") String paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}