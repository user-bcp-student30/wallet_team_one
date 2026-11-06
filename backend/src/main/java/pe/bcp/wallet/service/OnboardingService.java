package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.*;
import pe.bcp.wallet.dto.mock.MockKyc;
import pe.bcp.wallet.dto.mock.MockKycCreateRequest;
import pe.bcp.wallet.dto.mock.MockKycDecisionRequest;
import pe.bcp.wallet.entity.AnalyticsSpend;
import pe.bcp.wallet.entity.OnboardingEvent;
import pe.bcp.wallet.entity.OnboardingSession;
import pe.bcp.wallet.exception.AppException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Orquesta el flujo fijo de onboarding (IDENTITY -> DOCUMENT -> REVIEW ->
 * ACTIVATION). El mock solo participa en REVIEW (creación de KYC/wallet) y en
 * la decisión manual; el resto del flujo vive únicamente en el journal local.
 */
@ApplicationScoped
public class OnboardingService {

    private static final Pattern TEST_DOC_PATTERN = Pattern.compile("^TEST-.*");
    private static final Set<String> VALID_SCENARIOS = Set.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "UNAVAILABLE");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    @Transactional
    public OnboardingSessionResponse start() {
        OnboardingSession s = new OnboardingSession();
        s.onboardingId = UUID.randomUUID().toString();
        s.persist();

        OnboardingEvent ev = new OnboardingEvent();
        ev.onboardingId = s.onboardingId;
        ev.type = "ONBOARDING_STARTED";
        ev.persist();

        return toView(s);
    }

    @Transactional
    public OnboardingSessionResponse advanceStep(String onboardingId, OnboardingStepRequest req) {
        OnboardingSession s = findOrThrow(onboardingId);
        switch (s.currentStep) {
            case "IDENTITY" -> advanceIdentity(s, req);
            case "DOCUMENT" -> advanceDocument(s, req);
            case "REVIEW" -> advanceReview(s, req);
            case "ACTIVATION" -> advanceActivation(s);
            default -> throw new AppException(409, "ONBOARDING_TERMINAL", "El flujo de onboarding ya finalizó.", false);
        }
        s.persist();
        return toView(s);
    }

    private void advanceIdentity(OnboardingSession s, OnboardingStepRequest req) {
        if (req.fullName == null || req.fullName.isBlank()) {
            throw new AppException(400, "VALIDATION_ERROR", "fullName es obligatorio.", false);
        }
        if (!"TEST".equals(req.documentType)) {
            throw new AppException(400, "VALIDATION_ERROR", "documentType debe ser TEST.", false);
        }
        if (req.documentNumber == null || !TEST_DOC_PATTERN.matcher(req.documentNumber).matches()) {
            throw new AppException(400, "VALIDATION_ERROR", "El documento debe iniciar con TEST-", false);
        }
        s.fullName = req.fullName;
        s.documentType = req.documentType;
        s.documentNumber = req.documentNumber;
        s.identityStatus = "DONE";
        s.documentStatus = "CURRENT";
        s.currentStep = "DOCUMENT";
    }

    private void advanceDocument(OnboardingSession s, OnboardingStepRequest req) {
        if (req.accept == null || !req.accept) {
            throw new AppException(400, "VALIDATION_ERROR", "Debe aceptar la carga de documento (accept:true).", false);
        }
        s.documentStatus = "DONE";
        s.reviewStatus = "CURRENT";
        s.currentStep = "REVIEW";
    }

    private void advanceReview(OnboardingSession s, OnboardingStepRequest req) {
        if (req.scenario == null || !VALID_SCENARIOS.contains(req.scenario)) {
            throw new AppException(400, "VALIDATION_ERROR",
                    "scenario debe ser APPROVED, REJECTED, MANUAL_REVIEW o UNAVAILABLE.", false);
        }
        MockKyc kyc = mockClient.createKyc(new MockKycCreateRequest(s.documentType, s.documentNumber, req.scenario));
        s.scenario = req.scenario;
        s.kycId = kyc.id;
        s.walletId = kyc.walletId;
        s.walletStatus = kyc.status;
        s.reviewStatus = "DONE";
        s.activationStatus = "CURRENT";
        s.currentStep = "ACTIVATION";
    }

    private void advanceActivation(OnboardingSession s) {
        if ("ACTIVE".equals(s.walletStatus)) {
            s.activationStatus = "DONE";
            markCompletedIfNeeded(s);
        } else if ("REJECTED".equals(s.walletStatus)) {
            s.activationStatus = "DONE";
        }
        // PENDING (MANUAL_REVIEW): activationStatus se mantiene CURRENT, representa IN_REVIEW.
    }

    private void markCompletedIfNeeded(OnboardingSession s) {
        if (s.completedAt == null) {
            s.completedAt = Instant.now();
            OnboardingEvent ev = new OnboardingEvent();
            ev.onboardingId = s.onboardingId;
            ev.type = "ONBOARDING_COMPLETED";
            ev.persist();
        }
    }

    public OnboardingSessionResponse get(String onboardingId) {
        return toView(findOrThrow(onboardingId));
    }

    @Transactional
    public OnboardingSessionResponse manualReviewDecision(String onboardingId, ManualReviewDecisionRequest req) {
        OnboardingSession s = findOrThrow(onboardingId);
        if (!"PENDING".equals(s.walletStatus)) {
            throw new AppException(409, "NOT_IN_REVIEW", "La sesión no está en revisión manual.", false);
        }
        if (!"ACTIVE".equals(req.decision) && !"REJECTED".equals(req.decision)) {
            throw new AppException(400, "VALIDATION_ERROR", "decision debe ser ACTIVE o REJECTED.", false);
        }
        MockKyc kyc = mockClient.decideKyc(s.kycId, new MockKycDecisionRequest(req.decision));
        s.walletStatus = kyc.status;
        s.activationStatus = "DONE";
        if ("ACTIVE".equals(kyc.status)) {
            markCompletedIfNeeded(s);
        }
        s.persist();
        return toView(s);
    }

    public OnboardingAnalyticsResponse analytics() {
        List<OnboardingEvent> events = OnboardingEvent.listAll();
        Map<String, Instant> started = new HashMap<>();
        Map<String, Instant> completed = new HashMap<>();
        for (OnboardingEvent e : events) {
            if ("ONBOARDING_STARTED".equals(e.type)) {
                started.put(e.onboardingId, e.occurredAt);
            } else if ("ONBOARDING_COMPLETED".equals(e.type)) {
                completed.put(e.onboardingId, e.occurredAt);
            }
        }

        List<Long> durations = new ArrayList<>();
        Map<String, Long> monthlyCounts = new TreeMap<>();
        for (Map.Entry<String, Instant> entry : completed.entrySet()) {
            Instant startedAt = started.get(entry.getKey());
            if (startedAt != null) {
                durations.add(Duration.between(startedAt, entry.getValue()).getSeconds());
            }
            String month = entry.getValue().atZone(LIMA).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            monthlyCounts.merge(month, 1L, Long::sum);
        }
        Collections.sort(durations);
        Long median = durations.isEmpty() ? null : durations.get(durations.size() / 2);

        List<OnboardingAnalyticsResponse.MonthlyAnalytics> monthly = new ArrayList<>();
        for (Map.Entry<String, Long> e : monthlyCounts.entrySet()) {
            OnboardingAnalyticsResponse.MonthlyAnalytics m = new OnboardingAnalyticsResponse.MonthlyAnalytics();
            m.month = e.getKey();
            m.newActiveUsers = e.getValue();
            AnalyticsSpend spend = AnalyticsSpend.findById(e.getKey());
            m.acquisitionSpendPen = spend != null ? spend.amountPen : 0.0;
            m.cac = e.getValue() > 0 ? m.acquisitionSpendPen / e.getValue() : null;
            monthly.add(m);
        }

        OnboardingAnalyticsResponse r = new OnboardingAnalyticsResponse();
        r.medianOnboardingSeconds = median;
        r.monthly = monthly;
        return r;
    }

    @Transactional
    public void recordSpend(AnalyticsSpendRequest req) {
        AnalyticsSpend spend = AnalyticsSpend.findById(req.month);
        if (spend == null) {
            spend = new AnalyticsSpend();
            spend.month = req.month;
        }
        spend.amountPen = req.amountPen;
        spend.persist();
    }

    private OnboardingSessionResponse toView(OnboardingSession s) {
        OnboardingSessionResponse r = new OnboardingSessionResponse();
        r.onboardingId = s.onboardingId;
        r.currentStep = s.currentStep;
        r.steps = List.of(
                new OnboardingStepView("IDENTITY", s.identityStatus),
                new OnboardingStepView("DOCUMENT", s.documentStatus),
                new OnboardingStepView("REVIEW", s.reviewStatus),
                new OnboardingStepView("ACTIVATION", s.activationStatus)
        );
        r.walletId = s.walletId;
        r.walletStatus = s.walletStatus;
        return r;
    }

    private OnboardingSession findOrThrow(String id) {
        OnboardingSession s = OnboardingSession.findById(id);
        if (s == null) {
            throw new AppException(404, "NOT_FOUND", "Sesión de onboarding no encontrada.", false);
        }
        return s;
    }
}
