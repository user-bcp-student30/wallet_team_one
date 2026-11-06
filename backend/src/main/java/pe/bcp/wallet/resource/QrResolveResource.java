package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.QrResolveRequest;
import pe.bcp.wallet.dto.api.QrResolveResponse;
import pe.bcp.wallet.service.QrService;

@Path("/api/v1/qr/resolve")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class QrResolveResource {

    @Inject
    QrService service;

    @POST
    public QrResolveResponse resolve(@Valid QrResolveRequest body) {
        return service.resolveQr(body.payload);
    }
}
