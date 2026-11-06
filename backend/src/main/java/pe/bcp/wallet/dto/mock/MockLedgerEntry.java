package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockLedgerEntry {
    public long id;
    public String operation;
    public String kind;
    public String account;
    public long amount;
}
