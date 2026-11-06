package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockProviderPayment {
    public String id;
    public long amount;
    public String status;
}
