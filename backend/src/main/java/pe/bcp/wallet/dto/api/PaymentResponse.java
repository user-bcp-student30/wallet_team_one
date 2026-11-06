package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class PaymentResponse {
    public String paymentId;
    public String walletId;
    public String recipient;
    public long amountCents;
    public String currency = "PEN";
    public String status;
    public String reference;
    public boolean duplicate;
    public String nextAction;
    public Instant createdAt;
}
