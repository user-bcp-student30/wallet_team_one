package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.IncidentView;
import pe.bcp.wallet.dto.api.LedgerEntryView;
import pe.bcp.wallet.dto.api.ReconciliationRunResponse;
import pe.bcp.wallet.dto.api.SettlementView;
import pe.bcp.wallet.service.OpsService;
import pe.bcp.wallet.util.Dates;

import java.time.Instant;
import java.util.List;

@Path("/api/v1/ops")
@Produces(MediaType.APPLICATION_JSON)
public class OpsResource {

    @Inject
    OpsService service;

    @GET
    @Path("/ledger")
    public List<LedgerEntryView> ledger(@QueryParam("from") String from,
                                         @QueryParam("to") String to,
                                         @QueryParam("account") String account) {
        return service.ledger(Dates.parseOrNull(from), Dates.parseOrNull(to), account);
    }

    @GET
    @Path("/settlements")
    public List<SettlementView> settlements(@QueryParam("from") String from, @QueryParam("to") String to) {
        return service.settlements(Dates.parseOrNull(from), Dates.parseOrNull(to));
    }

    @POST
    @Path("/reconciliations/run")
    public ReconciliationRunResponse runReconciliation() {
        return service.runReconciliation();
    }

    @GET
    @Path("/reconciliations/incidents")
    public List<IncidentView> incidents(@QueryParam("from") String from,
                                         @QueryParam("to") String to,
                                         @QueryParam("channel") String channel) {
        return service.incidents(Dates.parseOrNull(from), Dates.parseOrNull(to), channel);
    }

    @GET
    @Path("/reconciliations/summary")
    public ReconciliationRunResponse summary(@QueryParam("date") String date) {
        return service.summary(date);
    }
}
