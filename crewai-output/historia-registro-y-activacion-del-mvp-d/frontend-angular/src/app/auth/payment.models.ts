// payment.models.ts

export type PaymentStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'EXPIRED' | 'REVERSED';

/** Cuerpo de POST /payments. `payeeId` es SUPUESTO PENDIENTE (S1). */
export interface CreatePaymentRequest {
  amountCents: number;
  currency: 'PEN';
  payeeId: string;
}

/** Forma asumida de la respuesta (S2). Solo los campos mínimos. */
export interface PaymentResponse {
  paymentId: string;
  status: PaymentStatus | string;
  amountCents: number;
  currency: string;
  createdAt?: string;
}

/** Formato de error del contrato (Arquitectura §1.2.5). */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  retryable?: boolean;
  traceId?: string;
}

export interface CreatePaymentResult {
  payment: PaymentResponse;
  /** true si el servidor respondió 200 (repetición idempotente); false si fue 201. */
  replayed: boolean;
}

/** Acción sugerida al usuario ante un error. */
export type ErrorAction = 'fix-form' | 'retry' | 'new-attempt' | 'none';

export interface UserFacingError {
  message: string;
  action: ErrorAction;
  /** Código de referencia para soporte. No es un dato sensible. */
  traceId?: string;
}

export type ErrorContext = 'create' | 'get';