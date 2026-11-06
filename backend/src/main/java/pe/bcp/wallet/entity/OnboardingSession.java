package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Estado local de una sesión de onboarding. El mock no modela el flujo de
 * pasos (IDENTITY/DOCUMENT/REVIEW/ACTIVATION); el BFF lo orquesta aquí y solo
 * llama al mock en el paso REVIEW (KYC) y en manual-review-decision.
 */
@Entity
@Table(name = "onboarding_session")
public class OnboardingSession extends PanacheEntityBase {

    @Id
    @Column(name = "onboarding_id")
    public String onboardingId;

    @Column(name = "current_step", nullable = false)
    public String currentStep = "IDENTITY";

    @Column(name = "identity_status", nullable = false)
    public String identityStatus = "CURRENT";
    @Column(name = "document_status", nullable = false)
    public String documentStatus = "PENDING";
    @Column(name = "review_status", nullable = false)
    public String reviewStatus = "PENDING";
    @Column(name = "activation_status", nullable = false)
    public String activationStatus = "PENDING";

    public String fullName;
    @Column(name = "document_type")
    public String documentType;
    @Column(name = "document_number")
    public String documentNumber;
    public String scenario;

    @Column(name = "kyc_id")
    public String kycId;
    @Column(name = "wallet_id")
    public String walletId;
    @Column(name = "wallet_status")
    public String walletStatus;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    public Instant completedAt;
}
