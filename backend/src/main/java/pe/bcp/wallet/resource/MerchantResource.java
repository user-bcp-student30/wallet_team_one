package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.bcp.wallet.dto.api.MerchantPaymentView;
import pe.bcp.wallet.dto.api.QrCreateRequest;
import pe.bcp.wallet.dto.api.QrResponse;
import pe.bcp.wallet.service.QrService;
import pe.bcp.wallet.util.Dates;

import java.time.Instant;
import java.util.List;

@Path("/api/v1/merchants/{merchantId}")
@Produces(MediaType.APPLICATION_JSON)
public class MerchantResource {

    @Inject
    QrService service;

    @POST
    @Path("/qr")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createQr(@PathParam("merchantId") String merchantId, QrCreateRequest body) {
        QrResponse r = service.createQr(merchantId, body == null ? new QrCreateRequest() : body);
        return Response.status(201).entity(r).build();
    }

    @GET
    @Path("/payments")
    public List<MerchantPaymentView> payments(@PathParam("merchantId") String merchantId,
                                               @QueryParam("from") String from,
                                               @QueryParam("to") String to,
                                               @QueryParam("reference") String reference) {
        Instant fromDate = Dates.parseOrNull(from);
        Instant toDate = Dates.parseOrNull(to);
        return service.listMerchantPayments(merchantId, fromDate, toDate, reference);
    }

    @POST
    @Path("/payments/{paymentId}/refresh")
    public MerchantPaymentView refresh(@PathParam("merchantId") String merchantId,
                                        @PathParam("paymentId") String paymentId) {
        return service.refreshMerchantPayment(merchantId, paymentId);
    }
}
