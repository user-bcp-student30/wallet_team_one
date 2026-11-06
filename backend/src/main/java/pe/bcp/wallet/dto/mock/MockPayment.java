package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockPayment {
    public String id;
    @JsonProperty("wallet_id")
    public String walletId;
    public String recipient;
    public long amount;
    public String status;
    public String scenario;
    public String idem;
}
