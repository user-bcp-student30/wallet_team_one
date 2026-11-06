package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.AdvanceProviderRequest;
import pe.bcp.wallet.dto.api.PaymentResponse;
import pe.bcp.wallet.dto.mock.MockCallback;
import pe.bcp.wallet.dto.mock.MockEventCreated;
import pe.bcp.wallet.dto.mock.MockProviderCompleteRequest;
import pe.bcp.wallet.entity.IdempotencyRecord;
import pe.bcp.wallet.entity.OnboardingEvent;
import pe.bcp.wallet.entity.OnboardingSession;
import pe.bcp.wallet.entity.PaymentJournal;
import pe.bcp.wallet.entity.QrJournal;
import pe.bcp.wallet.entity.ReconciliationIncidentSnapshot;
import pe.bcp.wallet.entity.ReconciliationSummary;
import pe.bcp.wallet.entity.SupportAction;

/** Utilidades de administración para pruebas end-to-end locales (no son historias de usuario). */
@ApplicationScoped
public class AdminService {

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @Inject
    PaymentService paymentService;

    @Transactional
    public boolean reset() {
        mockClient.reset();
        IdempotencyRecord.deleteAll();
        PaymentJournal.deleteAll();
        QrJournal.deleteAll();
        OnboardingSession.deleteAll();
        OnboardingEvent.deleteAll();
        SupportAction.deleteAll();
        ReconciliationIncidentSnapshot.deleteAll();
        ReconciliationSummary.deleteAll();
        return true;
    }

    @Transactional
    public PaymentResponse advanceProvider(String paymentId, AdvanceProviderRequest req) {
        String status = req.status == null ? "SUCCEEDED" : req.status.toUpperCase();
        MockEventCreated created = mockClient.completeProvider(paymentId, new MockProviderCompleteRequest(status));
        MockCallback callback = mockClient.deliverEvent(created.eventId);
        // Normaliza el estado final consultando/resolviendo de nuevo.
        return paymentService.resolve(paymentId);
    }
}
