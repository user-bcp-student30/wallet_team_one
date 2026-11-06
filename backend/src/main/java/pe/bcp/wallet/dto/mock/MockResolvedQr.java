package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockResolvedQr {
    public String merchantId;
    public String merchantName;
    public Long amountCents;
    public String currency;
}
