package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Primera observación local de un ProviderPayment de /settlements. */
@Entity
@Table(name = "settlement_observation")
public class SettlementObservation extends PanacheEntityBase {

    @Id
    @Column(name = "provider_payment_id")
    public String providerPaymentId;

    @Column(name = "observed_at", nullable = false)
    public Instant observedAt = Instant.now();
}
