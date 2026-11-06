package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MockReceipt {
    public String receiptId;
    public String paymentId;
    public long amountCents;
    public String currency;
    public String recipient;
    public String status;
}
