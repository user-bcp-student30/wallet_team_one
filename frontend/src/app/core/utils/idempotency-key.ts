/**
 * Generador y utilidades de clave de idempotencia (header `Idempotency-Key`).
 *
 * Contrato (ver CONTRACT.md, sección Pagos / historia de idempotencia):
 * - Cada intento de pago requiere una clave única por pagador+monto+destino+ventana
 *   de intento (criterio #1 de la historia de idempotencia).
 * - Si el usuario reintenta el MISMO pago (mismo monto/destino) tras un fallo o
 *   expiración, debe reutilizar la MISMA clave para que el backend lo trate como
 *   réplica idempotente y no cree un cobro duplicado (criterio #4 del payer).
 * - Si el usuario cambia el monto o el destino, se considera un intento distinto y
 *   se debe generar una clave NUEVA.
 */

/** Genera un identificador tipo UUID v4 usando crypto.randomUUID si está disponible
 * (navegadores modernos / Node 19+), con fallback manual para entornos sin esa API. */
export function generateIdempotencyKey(): string {
  const cryptoObj: Crypto | undefined = typeof crypto !== 'undefined' ? crypto : undefined;
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Calcula una huella del intento de pago (destino + monto + qrId) para decidir si
 * una nueva clave es necesaria o si corresponde reutilizar la existente. */
export function paymentFingerprint(recipient: string, amountCents: number, qrId?: string | null): string {
  return `${recipient}|${amountCents}|${qrId ?? ''}`;
}

/**
 * Mantiene en memoria la clave de idempotencia vigente para un intento de pago y
 * decide cuándo debe regenerarse (cambió el destino/monto) o reutilizarse (mismo
 * intento, por ejemplo tras un reintento del usuario sobre un resultado fallido).
 */
export class IdempotencyKeyTracker {
  private key: string | null = null;
  private fingerprint: string | null = null;

  /** Devuelve la clave a usar para este intento: reutiliza la vigente si la huella
   * (destino+monto+qrId) no cambió, o genera una nueva en caso contrario. */
  keyFor(recipient: string, amountCents: number, qrId?: string | null): string {
    const nextFingerprint = paymentFingerprint(recipient, amountCents, qrId);
    if (this.key && this.fingerprint === nextFingerprint) {
      return this.key;
    }
    this.fingerprint = nextFingerprint;
    this.key = generateIdempotencyKey();
    return this.key;
  }

  /** Limpia el estado, por ejemplo tras un pago terminal exitoso o al salir del
   * formulario, para que un nuevo intento posterior reciba una clave nueva. */
  reset(): void {
    this.key = null;
    this.fingerprint = null;
  }
}
