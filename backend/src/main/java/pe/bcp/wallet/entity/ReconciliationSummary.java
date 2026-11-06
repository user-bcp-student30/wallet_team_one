package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Resumen diario de conciliación persistido (uno por fecha America/Lima). */
@Entity
@Table(name = "reconciliation_summary")
public class ReconciliationSummary extends PanacheEntityBase {

    @Id
    @Column(name = "summary_date")
    public String date;

    @Column(nullable = false)
    public String status;

    public int reconciled;
    public int unreconciled;
    public int resolved;

    @Lob
    @Column(name = "incidents_json")
    public String incidentsJson;
}
