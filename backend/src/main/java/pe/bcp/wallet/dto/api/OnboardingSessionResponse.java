package pe.bcp.wallet.dto.api;

import java.util.List;

public class OnboardingSessionResponse {
    public String onboardingId;
    public String currentStep;
    public List<OnboardingStepView> steps;
    public String walletId;
    public String walletStatus;
}
