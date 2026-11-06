package pe.bcp.wallet.dto.mock;

public class MockPaymentCreateRequest {
    public String walletId;
    public String recipient;
    public long amountCents;
    public String currency;
    public String scenario;
    public String qrId;

    public MockPaymentCreateRequest() {
    }

    public MockPaymentCreateRequest(String walletId, String recipient, long amountCents,
                                     String currency, String scenario, String qrId) {
        this.walletId = walletId;
        this.recipient = recipient;
        this.amountCents = amountCents;
        this.currency = currency;
        this.scenario = scenario;
        this.qrId = qrId;
    }
}
