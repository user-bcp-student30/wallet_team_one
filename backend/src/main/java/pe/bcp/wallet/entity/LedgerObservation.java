package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Primera observación local de un asiento de ledger (para filtrar por fecha). */
@Entity
@Table(name = "ledger_observation")
public class LedgerObservation extends PanacheEntityBase {

    @Id
    @Column(name = "ledger_id")
    public Long ledgerId;

    @Column(name = "observed_at", nullable = false)
    public Instant observedAt = Instant.now();
}
