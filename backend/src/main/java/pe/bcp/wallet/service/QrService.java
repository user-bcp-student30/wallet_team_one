package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.*;
import pe.bcp.wallet.dto.mock.MockPayment;
import pe.bcp.wallet.dto.mock.MockQr;
import pe.bcp.wallet.dto.mock.MockQrCreateRequest;
import pe.bcp.wallet.dto.mock.MockQrResolveRequest;
import pe.bcp.wallet.dto.mock.MockResolvedQr;
import pe.bcp.wallet.entity.PaymentJournal;
import pe.bcp.wallet.entity.QrJournal;
import pe.bcp.wallet.exception.AppException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class QrService {

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @Inject
    PaymentService paymentService;

    @Transactional
    public QrResponse createQr(String merchantId, QrCreateRequest req) {
        Integer expires = req.expiresInSeconds == null ? 300 : req.expiresInSeconds;
        MockQrCreateRequest mockReq = new MockQrCreateRequest(merchantId, req.amountCents, expires);
        MockQr qr = mockClient.createQr(mockReq);

        QrJournal journal = new QrJournal();
        journal.qrId = qr.id;
        journal.merchantId = merchantId;
        journal.amountCents = qr.amountCents;
        journal.createdAt = Instant.now();
        journal.expiresAt = journal.createdAt.plusSeconds(expires);
        journal.persist();

        QrResponse r = new QrResponse();
        r.qrId = qr.id;
        r.payload = qr.payload;
        r.amountCents = qr.amountCents;
        r.currency = qr.currency;
        r.expiresAt = journal.expiresAt;
        return r;
    }

    public QrResolveResponse resolveQr(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new AppException(400, "VALIDATION_ERROR", "payload es obligatorio.", false);
        }
        MockResolvedQr resolved = mockClient.resolveQr(new MockQrResolveRequest(payload));
        QrResolveResponse r = new QrResolveResponse();
        r.merchantId = resolved.merchantId;
        r.merchantName = resolved.merchantName;
        r.amountCents = resolved.amountCents;
        r.currency = resolved.currency;
        return r;
    }

    @Transactional
    public List<MerchantPaymentView> listMerchantPayments(String merchantId, Instant from, Instant to, String reference) {
        List<MockPayment> payments = mockClient.listPayments("wallet-demo").stream()
                .filter(p -> merchantId.equals(p.recipient))
                .collect(Collectors.toList());

        return payments.stream().map(p -> {
            PaymentJournal j = PaymentJournal.findOrNull(p.id);
            if (j == null) {
                j = new PaymentJournal();
                j.paymentId = p.id;
                j.walletId = p.walletId;
                j.recipient = p.recipient;
                j.createdAt = Instant.now();
                j.updatedAt = j.createdAt;
                j.lastKnownStatus = p.status;
                j.persist();
            }
            MerchantPaymentView v = new MerchantPaymentView();
            v.paymentId = p.id;
            v.qrId = j.qrId;
            v.amountCents = p.amount;
            v.status = PaymentService.normalizeStatus(p.status);
            v.createdAt = j.createdAt;
            v.updatedAt = j.updatedAt;
            return v;
        })
        .filter(v -> reference == null || reference.equals(v.paymentId) || reference.equals(v.qrId))
        .filter(v -> from == null || !v.createdAt.isBefore(from))
        .filter(v -> to == null || !v.createdAt.isAfter(to))
        .collect(Collectors.toList());
    }

    @Transactional
    public MerchantPaymentView refreshMerchantPayment(String merchantId, String paymentId) {
        PaymentService.ResolveOutcome outcome = paymentService.resolveOnce(paymentId);
        MockPayment p = mockClient.getPayment(paymentId);
        PaymentJournal j = PaymentJournal.findOrNull(paymentId);
        MerchantPaymentView v = new MerchantPaymentView();
        v.paymentId = p.id;
        v.qrId = j != null ? j.qrId : null;
        v.amountCents = p.amount;
        v.status = outcome.escalated ? "PENDING" : PaymentService.normalizeStatus(p.status);
        v.createdAt = j != null ? j.createdAt : Instant.now();
        v.updatedAt = Instant.now();
        return v;
    }
}
