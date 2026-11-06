package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "qr_journal")
public class QrJournal extends PanacheEntityBase {

    @Id
    @Column(name = "qr_id")
    public String qrId;

    @Column(name = "merchant_id", nullable = false)
    public String merchantId;

    @Column(name = "amount_cents")
    public Long amountCents;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    public Instant expiresAt;
}
