package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.*;
import pe.bcp.wallet.dto.mock.*;
import pe.bcp.wallet.entity.IdempotencyRecord;
import pe.bcp.wallet.entity.PaymentJournal;
import pe.bcp.wallet.exception.AppException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PaymentService {

    private static final Set<String> VALID_RECIPIENTS = Set.of("contact-demo", "merchant-demo");

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @ConfigProperty(name = "payments.low-value-limit-cents")
    long lowValueLimitCents;

    public static class CreateResult {
        public PaymentResponse body;
        public boolean created;
        public String idempotencyKeyUsed;
    }

    @Transactional
    public CreateResult create(String idempotencyKey, PaymentCreateRequest req) {
        validate(req);

        String key = idempotencyKey;
        String fingerprint = fingerprint(req);

        IdempotencyRecord existing = IdempotencyRecord.findByWalletAndKey(req.walletId, key);
        CreateResult result = new CreateResult();
        result.idempotencyKeyUsed = key;

        if (existing != null) {
            if (!existing.fingerprint.equals(fingerprint)) {
                throw new AppException(409, "IDEMPOTENCY_CONFLICT",
                        "Ya existe una solicitud con la misma Idempotency-Key pero datos distintos.", false);
            }
            // Réplica: no se vuelve a crear, se refresca el estado actual.
            MockPayment current = mockClient.getPayment(existing.paymentId);
            result.body = toResponse(current, true, null);
            result.created = false;
            return result;
        }

        MockPaymentCreateRequest mockReq = new MockPaymentCreateRequest(
                req.walletId, req.recipient, req.amountCents, "PEN",
                req.scenario == null ? "APPROVED" : req.scenario, req.qrId);
        MockPayment created = mockClient.createPayment(key, mockReq);

        IdempotencyRecord record = new IdempotencyRecord();
        record.walletId = req.walletId;
        record.idempotencyKey = key;
        record.fingerprint = fingerprint;
        record.paymentId = created.id;
        record.persist();

        upsertJournal(created.id, req.walletId, req.recipient, req.qrId, created.status);

        ResolveOutcome outcome = resolveOnce(created.id);
        result.body = toResponseFromOutcome(created, outcome, false, req.amountCents);
        result.created = true;
        return result;
    }

    private void validate(PaymentCreateRequest req) {
        if (req.walletId == null || req.walletId.isBlank()) {
            throw new AppException(400, "VALIDATION_ERROR", "walletId es obligatorio.", false);
        }
        if (req.recipient == null || !VALID_RECIPIENTS.contains(req.recipient)) {
            throw new AppException(400, "VALIDATION_ERROR",
                    "recipient debe ser contact-demo o merchant-demo.", false);
        }
        if (req.amountCents == null || req.amountCents <= 0) {
            throw new AppException(400, "VALIDATION_ERROR", "amountCents debe ser mayor a 0.", false);
        }
        if (req.amountCents > lowValueLimitCents) {
            throw new AppException(400, "LOW_VALUE_LIMIT_EXCEEDED",
                    "El monto excede el límite de bajo valor de " + lowValueLimitCents + " céntimos.", false);
        }
    }

    private String fingerprint(PaymentCreateRequest req) {
        return req.recipient + "|" + req.amountCents + "|" + (req.qrId == null ? "" : req.qrId);
    }

    @Transactional
    void upsertJournal(String paymentId, String walletId, String recipient, String qrId, String status) {
        PaymentJournal j = PaymentJournal.findOrNull(paymentId);
        if (j == null) {
            j = new PaymentJournal();
            j.paymentId = paymentId;
            j.walletId = walletId;
            j.recipient = recipient;
            j.qrId = qrId;
            j.createdAt = Instant.now();
        } else if (qrId != null) {
            j.qrId = qrId;
        }
        j.lastKnownStatus = status;
        j.updatedAt = Instant.now();
        j.persist();
    }

    /** Resultado de intentar resolver un pago contra el proveedor simulado. */
    public static class ResolveOutcome {
        public boolean terminal;
        public boolean escalated;
        public String mockStatus;
    }

    @Transactional
    public ResolveOutcome resolveOnce(String paymentId) {
        Response resp = null;
        try {
            resp = mockClient.resolvePaymentRaw(paymentId);
            ResolveOutcome outcome = new ResolveOutcome();
            if (resp.getStatus() == 202) {
                MockPendingResolution pr = resp.readEntity(MockPendingResolution.class);
                outcome.terminal = false;
                outcome.escalated = pr.escalated;
                outcome.mockStatus = pr.status;
            } else {
                MockPayment p = resp.readEntity(MockPayment.class);
                outcome.terminal = true;
                outcome.escalated = false;
                outcome.mockStatus = p.status;
                upsertJournal(paymentId, p.walletId, p.recipient, null, p.status);
            }
            return outcome;
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    private PaymentResponse toResponseFromOutcome(MockPayment created, ResolveOutcome outcome,
                                                   boolean duplicate, long amountCents) {
        PaymentResponse r = new PaymentResponse();
        r.paymentId = created.id;
        r.walletId = created.walletId;
        r.recipient = created.recipient;
        r.amountCents = amountCents;
        r.reference = created.id;
        r.duplicate = duplicate;
        PaymentJournal j = PaymentJournal.findOrNull(created.id);
        r.createdAt = j != null ? j.createdAt : Instant.now();
        if (outcome.escalated) {
            r.status = "IN_REVIEW";
        } else {
            r.status = normalizeStatus(outcome.mockStatus != null ? outcome.mockStatus : created.status);
        }
        if ("FAILED".equals(r.status)) {
            r.nextAction = "RETRY";
        }
        return r;
    }

    private PaymentResponse toResponse(MockPayment p, boolean duplicate, Long amountCentsOverride) {
        PaymentResponse r = new PaymentResponse();
        r.paymentId = p.id;
        r.walletId = p.walletId;
        r.recipient = p.recipient;
        r.amountCents = amountCentsOverride != null ? amountCentsOverride : p.amount;
        r.status = normalizeStatus(p.status);
        r.reference = p.id;
        r.duplicate = duplicate;
        PaymentJournal j = PaymentJournal.findOrNull(p.id);
        if (j == null) {
            upsertJournal(p.id, p.walletId, p.recipient, null, p.status);
            j = PaymentJournal.findOrNull(p.id);
        }
        r.createdAt = j != null ? j.createdAt : Instant.now();
        if ("FAILED".equals(r.status)) {
            r.nextAction = "RETRY";
        }
        return r;
    }

    public static String normalizeStatus(String mockStatus) {
        if (mockStatus == null) {
            return "PENDING";
        }
        return switch (mockStatus) {
            case "REJECTED" -> "FAILED";
            case "PENDING", "SUCCEEDED", "REVERSED" -> mockStatus;
            default -> mockStatus;
        };
    }

    @Transactional
    public PaymentResponse get(String id) {
        MockPayment p = mockClient.getPayment(id);
        return toResponse(p, false, null);
    }

    @Transactional
    public PaymentResponse resolve(String id) {
        MockPayment current = mockClient.getPayment(id);
        ResolveOutcome outcome = resolveOnce(id);
        return toResponseFromOutcome(current, outcome, false, current.amount);
    }

    @Transactional
    public List<PaymentResponse> list(String walletId, String recipient, Instant from, Instant to, String status) {
        String wallet = walletId == null || walletId.isBlank() ? "wallet-demo" : walletId;
        List<MockPayment> payments = mockClient.listPayments(wallet);
        return payments.stream()
                .map(p -> toResponse(p, false, null))
                .filter(r -> recipient == null || recipient.equals(r.recipient))
                .filter(r -> status == null || status.equalsIgnoreCase(r.status))
                .filter(r -> from == null || !r.createdAt.isBefore(from))
                .filter(r -> to == null || !r.createdAt.isAfter(to))
                .collect(Collectors.toList());
    }

    public ReceiptResponse getReceipt(String id) {
        MockReceipt rec = mockClient.getReceipt(id);
        ReceiptResponse r = new ReceiptResponse();
        r.receiptId = rec.receiptId;
        r.paymentId = rec.paymentId;
        r.amountCents = rec.amountCents;
        r.currency = rec.currency;
        r.recipient = rec.recipient;
        r.status = rec.status;
        return r;
    }

    @Transactional
    public ReversalResponse reverse(String paymentId) {
        MockPayment payment = mockClient.getPayment(paymentId);
        Response resp = null;
        try {
            resp = mockClient.createReversalRaw(paymentId);
            MockReversal rev = resp.readEntity(MockReversal.class);
            ReversalResponse r = new ReversalResponse();
            r.reversalId = rev.id;
            r.paymentId = rev.paymentId;
            r.status = rev.status;
            upsertJournal(paymentId, payment.walletId, payment.recipient, null,
                    "REVERSED".equals(rev.status) ? "REVERSED" : "PENDING");
            return r;
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    public ReversalResponse getReversal(String id) {
        MockReversal rev = mockClient.getReversal(id);
        ReversalResponse r = new ReversalResponse();
        r.reversalId = rev.id;
        r.paymentId = rev.paymentId;
        r.status = rev.status;
        return r;
    }
}
