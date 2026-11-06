package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class ResolveCaseResponse {
    public String caseId;
    public String status;
    public String action;
    public String note;
    public String reference;
    public Instant resolvedAt;
}
