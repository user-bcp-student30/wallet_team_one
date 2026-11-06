package pe.bcp.wallet.dto.api;

/**
 * Cuerpo genérico para avanzar cualquier paso de onboarding; solo se validan
 * los campos relevantes al {@code currentStep} de la sesión.
 */
public class OnboardingStepRequest {
    public String fullName;
    public String documentType;
    public String documentNumber;
    public Boolean accept;
    public String scenario;
}
