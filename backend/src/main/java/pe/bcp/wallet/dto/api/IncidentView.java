package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class IncidentView {
    public String incidentId;
    public String paymentId;
    public String reason;
    public String status;
    public String channel = "wallet-mock";
    public Instant observedAt;
}
