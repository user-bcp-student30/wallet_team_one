package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class SettlementView {
    public String id;
    public long amountCents;
    public String status;
    public Instant observedAt;
}
