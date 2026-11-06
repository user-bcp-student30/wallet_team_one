package pe.bcp.wallet.dto.mock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Cuerpo de error devuelto por el mock bcp-wallet-mock. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MockError {
    public String error;
    public Boolean processed;
    public String paymentId;
    public String status;
}
