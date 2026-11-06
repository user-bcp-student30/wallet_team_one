import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/wallet.models';

/**
 * Traduce un `HttpErrorResponse` a un `ApiError` legible, asumiendo el formato
 * unificado de errores del BFF (ver CONTRACT.md):
 * `{ code, message, retryable, traceId }`.
 *
 * Si el backend no está disponible, o la respuesta no sigue ese formato (por
 * ejemplo un timeout de red o un 5xx genérico del proxy), se produce un
 * `ApiError` genérico para que la UI nunca muestre un stacktrace ni un mensaje
 * técnico crudo al usuario.
 */
export function mapHttpErrorToApiError(error: HttpErrorResponse): ApiError {
  const body = error.error;

  if (isApiErrorShape(body)) {
    return {
      code: body.code,
      message: body.message,
      retryable: Boolean(body.retryable),
      traceId: body.traceId ?? 'sin-trace-id'
    };
  }

  if (error.status === 0) {
    return {
      code: 'UPSTREAM_UNAVAILABLE',
      message: 'No se pudo conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.',
      retryable: true,
      traceId: 'sin-trace-id'
    };
  }

  return {
    code: `HTTP_${error.status}`,
    message: 'Ocurrió un error inesperado al comunicarse con el servidor. Inténtalo nuevamente.',
    retryable: error.status >= 500,
    traceId: 'sin-trace-id'
  };
}

function isApiErrorShape(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Record<string, unknown>)['code'] === 'string' &&
    typeof (value as Record<string, unknown>)['message'] === 'string'
  );
}

/** Mensajes en español, accionables, para los códigos de error de negocio más
 * comunes del contrato. Se usa como mejora sobre el `message` crudo del backend
 * cuando conviene un texto más guiado para el usuario final. */
const FRIENDLY_MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Revisa los datos ingresados: hay información obligatoria faltante o inválida.',
  LOW_VALUE_LIMIT_EXCEEDED: 'El monto supera el límite permitido para pagos de bajo monto (S/ 500.00).',
  IDEMPOTENCY_CONFLICT: 'Ya existe un intento distinto con esta misma clave de idempotencia.',
  INSUFFICIENT_FUNDS: 'La wallet no tiene saldo suficiente para completar esta operación.',
  WALLET_NOT_ACTIVE: 'La wallet no está activa. Completa el onboarding antes de continuar.',
  QR_EXPIRED: 'Este código QR expiró. Genera uno nuevo para continuar con el cobro.',
  PAYMENT_NOT_REVERSIBLE: 'Este pago no puede reversarse en su estado actual.',
  RECEIPT_NOT_READY: 'El comprobante aún no está disponible para este pago.',
  NOT_FOUND: 'No se encontró el recurso solicitado.',
  UPSTREAM_UNAVAILABLE: 'El servicio no está disponible en este momento. Inténtalo nuevamente en unos segundos.'
};

/** Devuelve el mensaje más amigable disponible para un `ApiError`: el mapeo
 * conocido por código si existe, o el mensaje tal cual provisto por el backend. */
export function friendlyErrorMessage(apiError: ApiError): string {
  return FRIENDLY_MESSAGES[apiError.code] ?? apiError.message;
}
