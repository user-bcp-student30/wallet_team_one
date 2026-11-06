package pe.bcp.wallet.dto.mock;

public class MockQrCreateRequest {
    public String merchantId;
    public Long amountCents;
    public Integer expiresInSeconds;

    public MockQrCreateRequest() {
    }

    public MockQrCreateRequest(String merchantId, Long amountCents, Integer expiresInSeconds) {
        this.merchantId = merchantId;
        this.amountCents = amountCents;
        this.expiresInSeconds = expiresInSeconds;
    }
}
