package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** Registro de auditoría de acciones de soporte sobre casos. */
@Entity
@Table(name = "support_action")
public class SupportAction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "case_id", nullable = false)
    public String caseId;

    @Column(nullable = false)
    public String action;

    public String note;

    @Column(name = "resolved_by", nullable = false)
    public String resolvedBy = "support-demo";

    @Column(name = "resolved_at", nullable = false)
    public Instant resolvedAt = Instant.now();

    @Column(name = "reference")
    public String reference;
}
