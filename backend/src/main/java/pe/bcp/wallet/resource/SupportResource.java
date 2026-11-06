package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.ResolveCaseRequest;
import pe.bcp.wallet.dto.api.ResolveCaseResponse;
import pe.bcp.wallet.dto.api.SupportCaseView;
import pe.bcp.wallet.service.SupportService;

import java.util.List;

@Path("/api/v1/support/cases")
@Produces(MediaType.APPLICATION_JSON)
public class SupportResource {

    @Inject
    SupportService service;

    @GET
    public List<SupportCaseView> list() {
        return service.listCases();
    }

    @POST
    @Path("/{caseId}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResolveCaseResponse resolve(@PathParam("caseId") String caseId, @Valid ResolveCaseRequest body) {
        return service.resolveCase(caseId, body);
    }
}
