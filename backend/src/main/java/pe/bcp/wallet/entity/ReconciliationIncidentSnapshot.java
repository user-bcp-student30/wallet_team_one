package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Snapshot de una incidencia de reconciliación para poder comparar
 * OPEN -> RESOLVED entre corridas (el mock no guarda historial de eso).
 */
@Entity
@Table(name = "reconciliation_incident_snapshot")
public class ReconciliationIncidentSnapshot extends PanacheEntityBase {

    @Id
    @Column(name = "incident_id")
    public String incidentId;

    @Column(nullable = false)
    public String status;

    @Column(name = "observed_at", nullable = false)
    public Instant observedAt = Instant.now();

    @Column(name = "last_seen_run_at", nullable = false)
    public Instant lastSeenRunAt = Instant.now();
}
