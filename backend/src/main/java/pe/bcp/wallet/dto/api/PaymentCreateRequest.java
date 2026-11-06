package pe.bcp.wallet.dto.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentCreateRequest {
    @NotBlank
    public String walletId;
    @NotBlank
    public String recipient;
    @NotNull
    @Min(1)
    public Long amountCents;
    public String scenario;
    public String qrId;
}
