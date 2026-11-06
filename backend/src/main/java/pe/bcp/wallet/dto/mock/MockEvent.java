package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockEvent {
    public String id;
    @JsonProperty("payment_id")
    public String paymentId;
    public String target;
    public int delivered;
}
