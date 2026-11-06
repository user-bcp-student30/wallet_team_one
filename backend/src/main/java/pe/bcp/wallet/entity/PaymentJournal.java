package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Primera observación local de un pago (el mock no persiste timestamps).
 * Permite filtrar historiales por fecha y asociar el qrId usado al crearlo.
 */
@Entity
@Table(name = "payment_journal")
public class PaymentJournal extends PanacheEntityBase {

    @Id
    @Column(name = "payment_id")
    public String paymentId;

    @Column(name = "wallet_id", nullable = false)
    public String walletId;

    @Column(nullable = false)
    public String recipient;

    @Column(name = "qr_id")
    public String qrId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "last_known_status")
    public String lastKnownStatus;

    public static PaymentJournal findOrNull(String paymentId) {
        return findById(paymentId);
    }
}
