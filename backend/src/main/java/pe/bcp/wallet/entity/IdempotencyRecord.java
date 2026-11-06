package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Registro de auditoría de idempotencia por (walletId, idempotencyKey).
 * Guarda el fingerprint de la solicitud original para detectar conflictos
 * (misma key, contenido distinto) y para servir réplicas sin volver a llamar
 * al mock.
 */
@Entity
@Table(name = "idempotency_record", uniqueConstraints = @UniqueConstraint(columnNames = {"wallet_id", "idempotency_key"}))
public class IdempotencyRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "wallet_id", nullable = false)
    public String walletId;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    @Column(nullable = false)
    public String fingerprint;

    @Column(name = "payment_id", nullable = false)
    public String paymentId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static IdempotencyRecord findByWalletAndKey(String walletId, String idempotencyKey) {
        return find("walletId = ?1 and idempotencyKey = ?2", walletId, idempotencyKey).firstResult();
    }
}
