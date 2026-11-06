package pe.bcp.wallet.dto.api;

import jakarta.validation.constraints.NotBlank;

public class ResolveCaseRequest {
    @NotBlank
    public String resolution;
    public String note;
}
