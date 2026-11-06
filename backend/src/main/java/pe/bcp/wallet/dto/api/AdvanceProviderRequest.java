package pe.bcp.wallet.dto.api;

import jakarta.validation.constraints.NotBlank;

public class AdvanceProviderRequest {
    @NotBlank
    public String status;
}
