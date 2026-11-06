package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.bcp.wallet.dto.api.PaymentCreateRequest;
import pe.bcp.wallet.dto.api.PaymentResponse;
import pe.bcp.wallet.dto.api.ReceiptResponse;
import pe.bcp.wallet.dto.api.ReversalResponse;
import pe.bcp.wallet.service.PaymentService;
import pe.bcp.wallet.util.Dates;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@HeaderParam("Idempotency-Key") String idempotencyKey, @Valid PaymentCreateRequest body) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        PaymentService.CreateResult result = service.create(key, body);
        int status = result.created ? 201 : 200;
        return Response.status(status)
                .header("Idempotency-Key", key)
                .entity(result.body)
                .build();
    }

    @GET
    @Path("/{id}")
    public PaymentResponse get(@PathParam("id") String id) {
        return service.get(id);
    }

    @GET
    public List<PaymentResponse> list(@QueryParam("walletId") String walletId,
                                       @QueryParam("recipient") String recipient,
                                       @QueryParam("from") String from,
                                       @QueryParam("to") String to,
                                       @QueryParam("status") String status) {
        Instant fromDate = Dates.parseOrNull(from);
        Instant toDate = Dates.parseOrNull(to);
        return service.list(walletId, recipient, fromDate, toDate, status);
    }

    @GET
    @Path("/{id}/receipt")
    public ReceiptResponse receipt(@PathParam("id") String id) {
        return service.getReceipt(id);
    }

    @POST
    @Path("/{id}/resolve")
    public PaymentResponse resolve(@PathParam("id") String id) {
        return service.resolve(id);
    }

    @POST
    @Path("/{id}/reversals")
    public ReversalResponse reverse(@PathParam("id") String id) {
        return service.reverse(id);
    }
}
