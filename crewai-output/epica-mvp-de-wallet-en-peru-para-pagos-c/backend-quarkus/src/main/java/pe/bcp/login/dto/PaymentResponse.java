// PaymentResponse.java
package pe.bcp.login.dto;

import pe.bcp.login.model.PaymentRecord;
import pe.bcp.login.model.PaymentStatus;

import java.time.Instant;

/**
 * Salida del pago. No expone identificadores internos del backend ni de la billetera pagadora.
 * Importe en céntimos de PEN.
 */
public record PaymentResponse(
        String paymentId,
        PaymentStatus status,
        long amountCents,
        String currency,
        String destination,
        String description,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(PaymentRecord record) {
        return new PaymentResponse(
                record.paymentId(),
                record.status(),
                record.amountCents(),
                record.currency(),
                record.destination(),
                record.description(),
                record.failureCode(),
                record.createdAt(),
                record.updatedAt());
    }
}