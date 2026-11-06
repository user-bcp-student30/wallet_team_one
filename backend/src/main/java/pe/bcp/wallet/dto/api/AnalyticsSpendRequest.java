package pe.bcp.wallet.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AnalyticsSpendRequest {
    @NotBlank
    public String month;
    @NotNull
    public Double amountPen;
}
