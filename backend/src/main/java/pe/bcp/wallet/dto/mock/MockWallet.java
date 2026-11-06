package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockWallet {
    public String id;
    public String status;
    public long balance;
    public long reserved;
    public long available;
    public String currency;
}
