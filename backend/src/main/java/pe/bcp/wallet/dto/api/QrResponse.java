package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class QrResponse {
    public String qrId;
    public String payload;
    public Long amountCents;
    public String currency;
    public Instant expiresAt;
}
