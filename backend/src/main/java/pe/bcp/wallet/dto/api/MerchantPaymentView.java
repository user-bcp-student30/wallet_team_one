package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class MerchantPaymentView {
    public String paymentId;
    public String qrId;
    public long amountCents;
    public String status;
    public Instant createdAt;
    public Instant updatedAt;
}
