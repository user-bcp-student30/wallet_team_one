package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.ReversalResponse;
import pe.bcp.wallet.service.PaymentService;

@Path("/api/v1/reversals")
@Produces(MediaType.APPLICATION_JSON)
public class ReversalResource {

    @Inject
    PaymentService service;

    @GET
    @Path("/{id}")
    public ReversalResponse get(@PathParam("id") String id) {
        return service.getReversal(id);
    }
}
