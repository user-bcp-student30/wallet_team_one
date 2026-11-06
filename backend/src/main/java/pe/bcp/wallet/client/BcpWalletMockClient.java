package pe.bcp.wallet.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import pe.bcp.wallet.dto.mock.*;

import java.util.List;

/**
 * Cliente REST reactivo (ejecutado en modo bloqueante) hacia el mock externo
 * bcp-wallet-mock. Un único interfaz cubre todas las operaciones consumidas
 * por el BFF según bcp-wallet-mock-openapi.yaml.
 */
@RegisterRestClient(configKey = "bcp-wallet-mock")
@RegisterProvider(MockErrorMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BcpWalletMockClient {

    @POST
    @Path("/kyc/verifications")
    MockKyc createKyc(MockKycCreateRequest body);

    @GET
    @Path("/kyc/verifications/{id}")
    MockKyc getKyc(@PathParam("id") String id);

    @POST
    @Path("/kyc/verifications/{id}/decision")
    MockKyc decideKyc(@PathParam("id") String id, MockKycDecisionRequest body);

    @GET
    @Path("/wallets/{id}")
    MockWallet getWallet(@PathParam("id") String id);

    @POST
    @Path("/payments")
    MockPayment createPayment(@HeaderParam("Idempotency-Key") String idempotencyKey, MockPaymentCreateRequest body);

    @GET
    @Path("/payments")
    List<MockPayment> listPayments(@QueryParam("walletId") String walletId);

    @GET
    @Path("/payments/{id}")
    MockPayment getPayment(@PathParam("id") String id);

    @GET
    @Path("/payments/{id}/receipt")
    MockReceipt getReceipt(@PathParam("id") String id);

    /**
     * 200 => body es Payment terminal; 202 => body es PendingResolution
     * (status:PENDING, escalated:true). Se expone como Response para poder
     * distinguir el esquema según el código de estado.
     */
    @POST
    @Path("/payments/{id}/resolve")
    Response resolvePaymentRaw(@PathParam("id") String id);

    @POST
    @Path("/payments/{id}/reversals")
    Response createReversalRaw(@PathParam("id") String id);

    @GET
    @Path("/reversals/{id}")
    MockReversal getReversal(@PathParam("id") String id);

    @POST
    @Path("/qrs")
    MockQr createQr(MockQrCreateRequest body);

    @POST
    @Path("/qrs/resolve")
    MockResolvedQr resolveQr(MockQrResolveRequest body);

    @GET
    @Path("/control/events")
    List<MockEvent> listEvents();

    @GET
    @Path("/ledger")
    List<MockLedgerEntry> getLedger();

    @GET
    @Path("/support/cases")
    List<MockSupportCase> getSupportCases();

    @GET
    @Path("/settlements")
    List<MockProviderPayment> getSettlements();

    @GET
    @Path("/reconciliations/incidents")
    List<MockIncident> getReconciliationIncidents();

    @POST
    @Path("/control/events/{id}/deliver")
    MockCallback deliverEvent(@PathParam("id") String id);

    @POST
    @Path("/control/provider/{id}/complete")
    MockEventCreated completeProvider(@PathParam("id") String id, MockProviderCompleteRequest body);

    @POST
    @Path("/reconciliations")
    MockReconciliation runReconciliation();

    @POST
    @Path("/control/reset")
    MockReset reset();
}
