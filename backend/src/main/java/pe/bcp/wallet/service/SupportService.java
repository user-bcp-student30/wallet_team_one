package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.ResolveCaseRequest;
import pe.bcp.wallet.dto.api.ResolveCaseResponse;
import pe.bcp.wallet.dto.api.SupportCaseView;
import pe.bcp.wallet.dto.mock.MockKycDecisionRequest;
import pe.bcp.wallet.dto.mock.MockPayment;
import pe.bcp.wallet.dto.mock.MockSupportCase;
import pe.bcp.wallet.entity.SupportAction;
import pe.bcp.wallet.exception.AppException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Casos de soporte: el mock los abre automáticamente (revisión KYC manual o
 * resultado de pago desconocido tras resolve). El BFF enriquece con
 * {@code resourceType}/{@code eligibleAction} y registra auditoría local de
 * cada resolución (criterio #4 de la historia de fallas/reversos).
 */
@ApplicationScoped
public class SupportService {

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @Inject
    PaymentService paymentService;

    public List<SupportCaseView> listCases() {
        List<MockSupportCase> cases = mockClient.getSupportCases();
        return cases.stream().map(this::toView).collect(Collectors.toList());
    }

    private SupportCaseView toView(MockSupportCase c) {
        SupportCaseView v = new SupportCaseView();
        v.caseId = c.id;
        v.resource = c.resource;
        v.reason = c.reason;
        v.status = c.status;
        v.resourceType = isPayment(c.resource) ? "PAYMENT" : "KYC";

        if ("PROVIDER_PENDING".equals(c.reason)) {
            v.eligibleAction = "INVESTIGATE";
        } else if ("PAYMENT".equals(v.resourceType)) {
            try {
                MockPayment p = mockClient.getPayment(c.resource);
                if ("REJECTED".equals(p.status)) {
                    v.eligibleAction = "RETRY_ELIGIBLE";
                } else if ("SUCCEEDED".equals(p.status) && "UNKNOWN_PAYMENT_RESULT".equals(c.reason)) {
                    // La transferencia ya se ejecutó: no hay cancelación unilateral.
                    v.eligibleAction = "DISPUTE_NOT_ELIGIBLE";
                } else {
                    v.eligibleAction = "INVESTIGATE";
                }
            } catch (Exception e) {
                v.eligibleAction = "INVESTIGATE";
            }
        } else {
            v.eligibleAction = "INVESTIGATE";
        }
        return v;
    }

    private boolean isPayment(String resource) {
        return resource != null && resource.startsWith("pay_");
    }

    @Transactional
    public ResolveCaseResponse resolveCase(String caseId, ResolveCaseRequest req) {
        MockSupportCase target = mockClient.getSupportCases().stream()
                .filter(c -> caseId.equals(c.id))
                .findFirst()
                .orElseThrow(() -> new AppException(404, "NOT_FOUND", "Caso de soporte no encontrado.", false));

        String resolution = req.resolution == null ? "" : req.resolution.toUpperCase();
        boolean isPayment = isPayment(target.resource);

        if (isPayment) {
            switch (resolution) {
                case "RETRY", "ESCALATE" -> paymentService.resolveOnce(target.resource);
                default -> { /* REJECT/ACCEPT_DISPUTE sobre un pago ya no son accionables en el mock */ }
            }
        } else {
            String decision = switch (resolution) {
                case "RETRY", "ACCEPT_DISPUTE" -> "ACTIVE";
                case "REJECT" -> "REJECTED";
                default -> null;
            };
            if (decision != null) {
                mockClient.decideKyc(target.resource, new MockKycDecisionRequest(decision));
            }
        }

        SupportAction action = new SupportAction();
        action.caseId = caseId;
        action.action = req.resolution;
        action.note = req.note;
        action.resolvedBy = "support-demo";
        action.resolvedAt = Instant.now();
        action.reference = target.resource;
        action.persist();

        ResolveCaseResponse r = new ResolveCaseResponse();
        r.caseId = caseId;
        r.status = "RESOLVED";
        r.action = req.resolution;
        r.note = req.note;
        r.reference = target.resource;
        r.resolvedAt = action.resolvedAt;
        return r;
    }
}
