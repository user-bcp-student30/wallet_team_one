package pe.bcp.wallet.dto.api;

import java.util.List;

public class OnboardingAnalyticsResponse {
    public Long medianOnboardingSeconds;
    public List<MonthlyAnalytics> monthly;

    public static class MonthlyAnalytics {
        public String month;
        public long newActiveUsers;
        public double acquisitionSpendPen;
        public Double cac;
    }
}
