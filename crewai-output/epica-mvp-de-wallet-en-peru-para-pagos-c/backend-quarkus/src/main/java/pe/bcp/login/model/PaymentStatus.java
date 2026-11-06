// PaymentStatus.java
package pe.bcp.login.model;

/**
 * Estados de dominio del pago, estables e independientes del mock.
 */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    EXPIRED,
    REVERSED
}