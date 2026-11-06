package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockSupportCase {
    public String id;
    public String resource;
    public String reason;
    public String status;
}
