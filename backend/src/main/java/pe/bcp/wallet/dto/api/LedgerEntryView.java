package pe.bcp.wallet.dto.api;

import java.time.Instant;

public class LedgerEntryView {
    public long id;
    public String operation;
    public String kind;
    public String account;
    public long amountCents;
    public Instant observedAt;
}
