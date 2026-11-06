package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.AdvanceProviderRequest;
import pe.bcp.wallet.dto.api.PaymentResponse;
import pe.bcp.wallet.dto.api.ResetResponse;
import pe.bcp.wallet.service.AdminService;

/**
 * Utilidades solo para pruebas end-to-end locales/demo (no son historias de usuario).
 */
@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AdminService service;

    @POST
    @Path("/reset")
    public ResetResponse reset() {
        ResetResponse r = new ResetResponse();
        r.reset = service.reset();
        return r;
    }

    @POST
    @Path("/payments/{id}/advance-provider")
    @Consumes(MediaType.APPLICATION_JSON)
    public PaymentResponse advanceProvider(@PathParam("id") String id, @Valid AdvanceProviderRequest body) {
        return service.advanceProvider(id, body);
    }
}
