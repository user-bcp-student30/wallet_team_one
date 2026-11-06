package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockQr {
    public String id;
    public String payload;
    public Long amountCents;
    public String currency;
}
