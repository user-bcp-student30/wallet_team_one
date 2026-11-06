package pe.bcp.wallet.dto.api;

public class OnboardingStepView {
    public String id;
    public String status;

    public OnboardingStepView() {
    }

    public OnboardingStepView(String id, String status) {
        this.id = id;
        this.status = status;
    }
}
