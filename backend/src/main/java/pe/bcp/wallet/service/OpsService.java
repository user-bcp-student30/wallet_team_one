package pe.bcp.wallet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.IncidentView;
import pe.bcp.wallet.dto.api.LedgerEntryView;
import pe.bcp.wallet.dto.api.ReconciliationRunResponse;
import pe.bcp.wallet.dto.api.SettlementView;
import pe.bcp.wallet.dto.mock.MockIncident;
import pe.bcp.wallet.dto.mock.MockLedgerEntry;
import pe.bcp.wallet.dto.mock.MockProviderPayment;
import pe.bcp.wallet.dto.mock.MockReconciliation;
import pe.bcp.wallet.entity.LedgerObservation;
import pe.bcp.wallet.entity.ReconciliationIncidentSnapshot;
import pe.bcp.wallet.entity.ReconciliationSummary;
import pe.bcp.wallet.entity.SettlementObservation;
import pe.bcp.wallet.exception.AppException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Operaciones y conciliación: proxies enriquecidos con fecha de primera observación local. */
@ApplicationScoped
public class OpsService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public List<LedgerEntryView> ledger(Instant from, Instant to, String account) {
        List<MockLedgerEntry> entries = mockClient.getLedger();
        return entries.stream().map(e -> {
            LedgerObservation obs = LedgerObservation.findById(e.id);
            if (obs == null) {
                obs = new LedgerObservation();
                obs.ledgerId = e.id;
                obs.observedAt = Instant.now();
                obs.persist();
            }
            LedgerEntryView v = new LedgerEntryView();
            v.id = e.id;
            v.operation = e.operation;
            v.kind = e.kind;
            v.account = e.account;
            v.amountCents = e.amount;
            v.observedAt = obs.observedAt;
            return v;
        })
        .filter(v -> account == null || account.equals(v.account))
        .filter(v -> from == null || !v.observedAt.isBefore(from))
        .filter(v -> to == null || !v.observedAt.isAfter(to))
        .collect(Collectors.toList());
    }

    @Transactional
    public List<SettlementView> settlements(Instant from, Instant to) {
        List<MockProviderPayment> payments = mockClient.getSettlements();
        return payments.stream().map(p -> {
            SettlementObservation obs = SettlementObservation.findById(p.id);
            if (obs == null) {
                obs = new SettlementObservation();
                obs.providerPaymentId = p.id;
                obs.observedAt = Instant.now();
                obs.persist();
            }
            SettlementView v = new SettlementView();
            v.id = p.id;
            v.amountCents = p.amount;
            v.status = p.status;
            v.observedAt = obs.observedAt;
            return v;
        })
        .filter(v -> from == null || !v.observedAt.isBefore(from))
        .filter(v -> to == null || !v.observedAt.isAfter(to))
        .collect(Collectors.toList());
    }

    @Transactional
    public List<IncidentView> incidents(Instant from, Instant to, String channel) {
        List<MockIncident> incidents = mockClient.getReconciliationIncidents();
        return incidents.stream().map(this::toView)
                .filter(v -> channel == null || channel.equals(v.channel))
                .filter(v -> from == null || !v.observedAt.isBefore(from))
                .filter(v -> to == null || !v.observedAt.isAfter(to))
                .collect(Collectors.toList());
    }

    private IncidentView toView(MockIncident i) {
        ReconciliationIncidentSnapshot snap = ReconciliationIncidentSnapshot.findById(i.id);
        if (snap == null) {
            snap = new ReconciliationIncidentSnapshot();
            snap.incidentId = i.id;
            snap.status = i.status;
            snap.observedAt = Instant.now();
        }
        snap.status = i.status;
        snap.lastSeenRunAt = Instant.now();
        snap.persist();

        IncidentView v = new IncidentView();
        v.incidentId = i.id;
        v.paymentId = i.paymentId;
        v.reason = i.reason;
        v.status = i.status;
        v.observedAt = snap.observedAt;
        return v;
    }

    @Transactional
    public ReconciliationRunResponse runReconciliation() {
        MockReconciliation result = mockClient.runReconciliation();
        String today = Instant.now().atZone(LIMA).format(DATE_FMT);

        int resolvedCount = 0;
        List<IncidentView> openIncidents = result.differences == null ? List.of() :
                result.differences.stream().filter(i -> "OPEN".equals(i.status)).map(this::toView).collect(Collectors.toList());

        if (result.differences != null) {
            for (MockIncident incident : result.differences) {
                ReconciliationIncidentSnapshot prev = ReconciliationIncidentSnapshot.findById(incident.id);
                if (prev != null && "OPEN".equals(prev.status) && "RESOLVED".equals(incident.status)) {
                    resolvedCount++;
                }
                toView(incident);
            }
        }

        int unreconciled = openIncidents.size();
        long totalPayments = mockClient.listPayments("wallet-demo").size();
        int reconciled = (int) Math.max(0, totalPayments - unreconciled);

        ReconciliationSummary summary = ReconciliationSummary.findById(today);
        if (summary == null) {
            summary = new ReconciliationSummary();
            summary.date = today;
        }
        summary.status = result.status;
        summary.reconciled = reconciled;
        summary.unreconciled = unreconciled;
        summary.resolved = resolvedCount;
        summary.incidentsJson = toJson(openIncidents);
        summary.persist();

        ReconciliationRunResponse r = new ReconciliationRunResponse();
        r.date = today;
        r.status = result.status;
        r.totals = new ReconciliationRunResponse.Totals();
        r.totals.reconciled = reconciled;
        r.totals.unreconciled = unreconciled;
        r.totals.resolved = resolvedCount;
        r.incidents = openIncidents;
        return r;
    }

    public ReconciliationRunResponse summary(String date) {
        ReconciliationSummary summary = ReconciliationSummary.findById(date);
        if (summary == null) {
            throw new AppException(404, "NOT_FOUND", "No hay resumen de conciliación para " + date, false);
        }
        ReconciliationRunResponse r = new ReconciliationRunResponse();
        r.date = summary.date;
        r.status = summary.status;
        r.totals = new ReconciliationRunResponse.Totals();
        r.totals.reconciled = summary.reconciled;
        r.totals.unreconciled = summary.unreconciled;
        r.totals.resolved = summary.resolved;
        r.incidents = fromJson(summary.incidentsJson);
        return r;
    }

    private String toJson(List<IncidentView> incidents) {
        try {
            return objectMapper.writeValueAsString(incidents);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<IncidentView> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<IncidentView>>() { });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
