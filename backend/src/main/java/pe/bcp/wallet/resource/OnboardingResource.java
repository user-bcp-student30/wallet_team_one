package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.bcp.wallet.dto.api.*;
import pe.bcp.wallet.service.OnboardingService;

@Path("/api/v1/onboarding")
@Produces(MediaType.APPLICATION_JSON)
public class OnboardingResource {

    @Inject
    OnboardingService service;

    @POST
    @Path("/sessions")
    public Response start() {
        OnboardingSessionResponse r = service.start();
        return Response.status(201).entity(r).build();
    }

    @POST
    @Path("/sessions/{onboardingId}/steps")
    @Consumes(MediaType.APPLICATION_JSON)
    public OnboardingSessionResponse advance(@PathParam("onboardingId") String onboardingId,
                                              OnboardingStepRequest body) {
        return service.advanceStep(onboardingId, body == null ? new OnboardingStepRequest() : body);
    }

    @GET
    @Path("/sessions/{onboardingId}")
    public OnboardingSessionResponse get(@PathParam("onboardingId") String onboardingId) {
        return service.get(onboardingId);
    }

    @POST
    @Path("/sessions/{onboardingId}/manual-review-decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public OnboardingSessionResponse manualReviewDecision(@PathParam("onboardingId") String onboardingId,
                                                            @Valid ManualReviewDecisionRequest body) {
        return service.manualReviewDecision(onboardingId, body);
    }

    @GET
    @Path("/analytics")
    public OnboardingAnalyticsResponse analytics() {
        return service.analytics();
    }

    @POST
    @Path("/analytics/spend")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response recordSpend(@Valid AnalyticsSpendRequest body) {
        service.recordSpend(body);
        return Response.ok().build();
    }
}
