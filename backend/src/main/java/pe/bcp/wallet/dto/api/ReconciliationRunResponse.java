package pe.bcp.wallet.dto.api;

import java.util.List;

public class ReconciliationRunResponse {
    public String date;
    public String status;
    public Totals totals;
    public List<IncidentView> incidents;

    public static class Totals {
        public int reconciled;
        public int unreconciled;
        public int resolved;
    }
}
