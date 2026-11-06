package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "onboarding_event")
public class OnboardingEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "onboarding_id", nullable = false)
    public String onboardingId;

    @Column(nullable = false)
    public String type;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt = Instant.now();
}
