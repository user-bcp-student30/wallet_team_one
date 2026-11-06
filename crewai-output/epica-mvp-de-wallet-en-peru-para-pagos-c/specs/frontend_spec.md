# frontend_spec.md

> **Estado: NO EJECUTADO.** No ejecuté `ng build`, `ng test`, `ng serve` ni pruebas manuales. No afirmo que el código compile, que las pruebas pasen ni que la integración con el BFF funcione. Todo está pendiente de ejecutar con los comandos de la sección 8.
>
> **Información recibida y sus límites**
> - El contrato de Arquitectura y la especificación de Backend llegaron **truncados**: no vi esquemas completos, rutas adicionales ni la lista completa de códigos de error.
> - No recibí el OpenAPI del mock, el modelo de Datos ni el código del proyecto Angular.
> - El Issue es una épica sin criterios de aceptación numerados. Uso los **criterios derivados CA-1…CA-12 del Backend**, que no son oficiales y deben confirmarse.
> - **Alcance entregado:** el único corte vertical que Backend implementó, es decir crear y consultar pagos de bajo monto con idempotencia (`POST /api/v1/payments`, `GET /api/v1/payments/{paymentId}`).
> - **Fuera de este entregable:** onboarding (#2), vista de comercio/QR (#4), conciliación (#5) y reversos (#7). Backend los declaró no implementados y no inventé endpoints para ellos.
> - **Correcciones:** no recibí observaciones de revisión, así que no hay cambios que reportar.

## 1. Recorrido y pantallas

| Ruta (SUPUESTO) | Componente | Propósito |
|---|---|---|
| `/pagos/nuevo` | `PaymentCreateComponent` | El usuario ingresa destinatario y monto en soles, confirma y ve el resultado. |
| `/pagos/:paymentId` | `PaymentDetailComponent` | Consulta el estado de un pago y lo actualiza manualmente. |

Recorrido principal:
1. El usuario abre `/pagos/nuevo` y ve el formulario vacío.
2. Completa destinatario y monto. Las validaciones se muestran al salir del campo o al enviar.
3. Pulsa "Pagar". El botón se deshabilita y aparece "Procesando pago…".
4. Si el servidor responde 201 o 200, se muestra el resultado con el estado en texto y el enlace "Ver detalle".
5. Si hay error, se muestra un mensaje comprensible con la acción adecuada: corregir, reintentar con la misma solicitud o iniciar un pago nuevo.

## 2. Campos del formulario y validaciones visibles

| Campo | Control | Validaciones del cliente (solo apoyo) | Mensaje visible | Va al servidor como |
|---|---|---|---|---|
| Destinatario | `<select>` con "Comercio demo" (`merchant-demo`) y "Contacto demo" (`contact-demo`) | requerido | "Selecciona un destinatario." | `payeeId` **[SUPUESTO PENDIENTE: nombre del campo y si es requerido. Define Backend]** |
| Monto (S/) | `<input type="text" inputmode="decimal">` | requerido; formato `123` o `123.45` (se acepta coma); máximo 7 dígitos enteros y 2 decimales; mayor que 0 | "Ingresa el monto." / "Usa solo números con hasta 2 decimales. Ejemplo: 12.50" / "El monto debe ser mayor que S/ 0.00." | `amountCents` (entero en céntimos, confirmado por Backend CA-5) |
| Moneda | Texto fijo "Soles (PEN)", no editable | n/a | n/a | `currency: "PEN"` (D7) |
| `Idempotency-Key` | No visible. Se genera en el cliente | Patrón del servidor `^[A-Za-z0-9_-]{8,64}$`; el cliente genera `pay-` + 32 hex = 36 caracteres | n/a | Header `Idempotency-Key` |

- **Límite máximo de monto:** el servidor valida un límite (CA-5), pero no conozco su valor. El cliente no lo valida. Si el servidor responde 400, se muestra un mensaje genérico. **SUPUESTO PENDIENTE: Backend debe publicar el límite y el código de error.**
- **Reglas de la clave de idempotencia:**
  - Se genera al primer envío y se **conserva en memoria** (nunca en `localStorage`) mientras la solicitud no cambie.
  - Un reintento tras error de red, 5xx o `IDEMPOTENCY_IN_PROGRESS` reutiliza la misma clave y el mismo cuerpo.
  - Si el usuario cambia destinatario o monto, se genera una clave nueva.
  - Tras un éxito, la clave se descarta.

## 3. Acciones del usuario

| Acción | Efecto |
|---|---|
| Pagar | Valida, deshabilita el envío y llama `POST /api/v1/payments`. |
| Reintentar | Repite la misma solicitud con la misma clave (solo si el error es reintentable). |
| Iniciar un pago nuevo | Descarta la clave y limpia el error o el resultado. |
| Ver detalle | Navega a `/pagos/:paymentId`. |
| Actualizar estado | Llama `GET /api/v1/payments/{paymentId}`. No hay polling automático. **SUPUESTO**: el contrato recibido no define la frecuencia. |

## 4. Estados de pantalla

| Estado | Pantalla de creación | Pantalla de detalle |
|---|---|---|
| Vacío | Formulario sin datos y texto "Todavía no has enviado ningún pago en esta sesión". No existe endpoint de listado: Backend no lo implementó y no lo invento. | No aplica. Si falta el id en la ruta, se muestra "Falta el identificador del pago". |
| Carga | Botón deshabilitado, `aria-busy="true"` y mensaje "Procesando pago…" (`role="status"`). | "Cargando pago…" (`role="status"`). |
| Éxito | Panel de resultado con foco y estado en texto. Si fue 200, se avisa que el pago ya estaba registrado. | Datos del pago y botón "Actualizar estado". |
| Error | Panel `role="alert"` con mensaje y acción. | Panel `role="alert"` con mensaje y botón "Reintentar". |

Los estados de dominio se muestran con texto, no solo con color:

| Estado | Texto |
|---|---|
| `PENDING` | En proceso |
| `CONFIRMED` | Confirmado |
| `FAILED` | No completado |
| `EXPIRED` | Expirado |
| `REVERSED` | Revertido |

Cualquier otro valor se muestra como "Estado no reconocido".

## 5. Mapeo de errores de API a mensajes

El formato de error es `{code, message, retryable, traceId}` (Arquitectura §1.2.5 y Backend). El cliente **no muestra `message` del servidor tal cual**: mapea por `code` y por estado HTTP. Del servidor solo usa `traceId`, como "Código de referencia" para soporte.

| Origen | Condición | Mensaje al usuario | Acción |
|---|---|---|---|
| 0 | Sin conexión o timeout | "No pudimos conectarnos. Revisa tu conexión e inténtalo de nuevo." | Reintentar (misma clave) |
| 400 | Crear | "Revisa los datos del pago: alguno no es válido." | Corregir el formulario |
| 400 | Consultar | "El identificador del pago no es válido." | Ninguna |
| 401 | Cualquiera | "Tu sesión no es válida o venció. Vuelve a iniciar sesión." | Ninguna. **SUPUESTO PENDIENTE: Backend no definió autenticación (CA-13).** |
| 403 | Cualquiera | "No tienes permiso para realizar esta acción." | Ninguna. **SUPUESTO PENDIENTE (igual que 401).** |
| 404 | Consultar | "No encontramos este pago. Verifica el identificador." | Ninguna |
| 404 | Crear | "No pudimos completar la operación. Inténtalo de nuevo más tarde." | Ninguna. **SUPUESTO**: el contrato no define 404 en creación. |
| 409 `IDEMPOTENCY_IN_PROGRESS` | Crear | "Tu pago se está procesando. Espera unos segundos y vuelve a intentarlo." | Reintentar (misma clave) |
| 409 `IDEMPOTENCY_KEY_REUSED` | Crear | "Esta solicitud ya se usó con otros datos. Inicia un pago nuevo." | Iniciar pago nuevo |
| 409 otro código | Cualquiera | "La operación entra en conflicto con otra ya registrada. Verifica el estado antes de volver a intentar." | Ninguna |
| 5xx | Crear | "El servicio no está disponible por ahora. Puedes reintentar: se usará la misma solicitud para evitar cobros duplicados." | Reintentar (misma clave) |
| 5xx | Consultar | "El servicio no está disponible por ahora. Inténtalo en unos minutos." | Reintentar |
| Otros | Cualquiera | "Ocurrió un error inesperado. Inténtalo de nuevo." | Ninguna |

Los códigos de dominio de fallo de pago (#7) que Backend no listó en lo recibido caen en el mensaje por estado HTTP. **SUPUESTO PENDIENTE: Backend debe entregar la lista completa de `code`.**

## 6. Accesibilidad

- Cada campo tiene `<label for>` y `aria-describedby` hacia su ayuda y su error. Los campos inválidos llevan `aria-invalid="true"`.
- Los errores de campo y los errores de API usan `role="alert"`. Carga y resultado usan `role="status"`.
- Al enviar un formulario inválido, el foco va al primer campo inválido. Tras un error de API, el foco va al panel de error. Tras un éxito, va al título del resultado (`tabindex="-1"`).
- Todo es operable con teclado: controles nativos, orden lógico y `:focus-visible` con contorno de 3 px.
- El estado se comunica con texto, no solo con color. Los colores del SCSS buscan contraste de al menos 4.5:1, pero **no medí el contraste**; verificarlo en revisión.
- No se usa `innerHTML`. Todo se renderiza con interpolación de Angular.
- No se guardan tokens ni secretos. La clave de idempotencia vive solo en memoria del componente.

## 7. Evidencia: criterio → archivo y elemento

| Criterio (derivado) | Archivo y elemento | Cómo comprobarlo |
|---|---|---|
| CA-1 `Idempotency-Key` obligatoria | `payments.service.ts` (`createPayment`, header); `idempotency-key.ts` | Spec del servicio "envía el header Idempotency-Key". Manual: Network → cabecera presente. |
| CA-2 reintento sin duplicar | `payment-create.component.ts` (`submit`, `attemptKey`) | Spec "reutiliza la misma clave al reintentar". Manual: cortar la red, enviar, restaurar, reintentar y comparar claves. |
| CA-3 misma clave con otra solicitud | `api-error.mapper.ts` (`IDEMPOTENCY_KEY_REUSED`); botón "Iniciar un pago nuevo" | Spec del mapper; manual con un mock del 409. |
| CA-4 en curso | `api-error.mapper.ts` (`IDEMPOTENCY_IN_PROGRESS`); botón "Reintentar" | Spec del mapper. |
| CA-5 céntimos, moneda PEN | `money.ts` (`toCents`); `payment-create.component.ts` (`buildPayload`) | Spec de `toCents`: "12.50" → 1250. |
| CA-6 estados claros | `payment-status.ts`; paneles de resultado y detalle | Manual con respuestas simuladas de cada estado. |
| CA-8 consulta por id y 404 | `payment-detail.component.ts` | Spec del mapper (404 en consulta); manual con un id inexistente. |
| CA-9 validación servidor 400 | `api-error.mapper.ts` (400) | Spec del mapper. |
| CA-10 errores sin técnicos | `api-error.mapper.ts` (solo mensajes propios y `traceId`) | Spec: el mensaje no contiene el `message` del servidor. |
| CA-11 fallo/5xx del mock | `api-error.mapper.ts` (5xx, 0) | Spec del mapper. |
| CA-12 expirado | `payment-status.ts` (`EXPIRED`) | Solo la presentación. **El estado lo deriva Backend y su cobertura es "No cubierto" allí.** |
| CA-13 401/403 | `api-error.mapper.ts` | Mensaje genérico. **SUPUESTO PENDIENTE.** |
| Historias #2, #4, #5, #7 | n/a | **No cubiertas**: no existe contrato implementado. |

## 8. Pruebas y comandos (NO EJECUTADO)

```
ng test --watch=false --browsers=ChromeHeadless
ng build
```

- **Pruebas automáticas:** `api-error.mapper.spec.ts`, `payments.service.spec.ts` y `payment-create.component.spec.ts`.
- **Pasos manuales sugeridos** (con el BFF o un mock HTTP):
  1. Enviar un formulario vacío y comprobar mensajes y foco.
  2. Enviar un pago válido y comprobar el estado de carga y el resultado.
  3. Simular 409, 5xx y corte de red y verificar el mensaje y el botón.
  4. Navegar solo con teclado.
  5. Probar con un lector de pantalla que los errores se anuncian.
- **Cuidado con `POST /control/reset`:** no se usa desde el frontend. Es compartido y destructivo, y debe coordinarse con el equipo.

## 9. Supuestos pendientes

| # | Supuesto | Responsable |
|---|---|---|
| S1 | Nombre y obligatoriedad de `payeeId`; si hay otros campos requeridos (por ejemplo la wallet pagadora, ya que la respuesta omite `payerWalletId`). | Backend / Arquitectura |
| S2 | Forma exacta de `PaymentResponse`. Asumo `paymentId`, `status`, `amountCents`, `currency`, `createdAt?`. | Backend |
| S3 | Límite máximo de monto y código de error asociado. | Backend |
| S4 | Autenticación (401/403): no definida. | Arquitectura / Backend |
| S5 | URL base del BFF: asumo `/api/v1` relativo con proxy de desarrollo (`proxy.conf.json`). El host es **desconocido**. | Arquitectura / DevOps |
| S6 | Versión de Angular: asumo 17 o superior (standalone, `@if`, `inject`). | Arquitectura (#8) |
| S7 | Lista completa de `code` de error, incluidos los de fallo de pago. | Backend |
| S8 | Frecuencia de actualización del estado (hoy solo manual). | Producto / Backend |
| S9 | Rutas del frontend `/pagos/...`. | Producto |
| S10 | Pantallas de onboarding, comercio/QR, conciliación y reversos. | Backend, tras definir contrato |

---

## Código Angular

```ts
// api-config.ts
import { InjectionToken } from '@angular/core';

/**
 * URL base del BFF. SUPUESTO PENDIENTE (S5): se asume ruta relativa con proxy
 * de desarrollo. Sobrescribir en app.config.ts según el entorno.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api/v1',
});
```

```ts
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
```

```ts
// money.ts

/**
 * Convierte texto en soles ("12.50", "12,5", "7") a céntimos enteros.
 * Devuelve null si el formato no es válido. No usa coma flotante.
 * Máximo 7 dígitos enteros y 2 decimales.
 */
export function toCents(input: string): number | null {
  const normalized = (input ?? '').trim().replace(',', '.');
  const match = /^(\d{1,7})(?:\.(\d{1,2}))?$/.exec(normalized);
  if (!match) {
    return null;
  }
  const whole = parseInt(match[1], 10);
  const fraction = parseInt((match[2] ?? '').padEnd(2, '0') || '0', 10);
  return whole * 100 + fraction;
}

export function formatCents(cents: number, currency = 'PEN'): string {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency }).format(cents / 100);
}
```

```ts
// idempotency-key.ts

/**
 * Genera una clave que cumple el patrón del servidor ^[A-Za-z0-9_-]{8,64}$.
 * Formato: "pay-" + 32 caracteres hexadecimales (36 en total).
 * Usa crypto.getRandomValues, disponible también fuera de contextos seguros.
 */
export function generateIdempotencyKey(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `pay-${hex}`;
}
```

```ts
// payment-status.ts
import { PaymentStatus } from './payment.models';

export interface StatusInfo {
  label: string;
  description: string;
}

const STATUS_INFO: Record<PaymentStatus, StatusInfo> = {
  PENDING: { label: 'En proceso', description: 'Tu pago se está procesando. Actualiza el estado en unos instantes.' },
  CONFIRMED: { label: 'Confirmado', description: 'Tu pago fue confirmado.' },
  FAILED: { label: 'No completado', description: 'El pago no se pudo completar.' },
  EXPIRED: { label: 'Expirado', description: 'El pago expiró antes de completarse.' },
  REVERSED: { label: 'Revertido', description: 'El pago fue revertido.' },
};

const UNKNOWN: StatusInfo = {
  label: 'Estado no reconocido',
  description: 'No pudimos interpretar el estado del pago. Inténtalo más tarde.',
};

export function statusInfo(status: string | undefined): StatusInfo {
  return (status && STATUS_INFO[status as PaymentStatus]) || UNKNOWN;
}
```

```ts
// api-error.mapper.ts
import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody, ErrorContext, UserFacingError } from './payment.models';

const GENERIC = 'Ocurrió un error inesperado. Inténtalo de nuevo.';

function readBody(raw: unknown): ApiErrorBody | null {
  if (raw && typeof raw === 'object') {
    return raw as ApiErrorBody;
  }
  return null;
}

/**
 * Traduce un error HTTP a un mensaje para el usuario.
 * Nunca muestra el `message` del servidor ni trazas: solo mensajes propios
 * y el traceId como código de referencia.
 */
export function mapHttpError(err: unknown, context: ErrorContext): UserFacingError {
  if (!(err instanceof HttpErrorResponse)) {
    return { message: GENERIC, action: 'none' };
  }

  const body = readBody(err.error);
  const traceId = typeof body?.traceId === 'string' ? body.traceId : undefined;
  const code = typeof body?.code === 'string' ? body.code : undefined;
  const make = (message: string, action: UserFacingError['action']): UserFacingError => ({
    message,
    action,
    traceId,
  });

  if (err.status === 0) {
    return make('No pudimos conectarnos. Revisa tu conexión e inténtalo de nuevo.', 'retry');
  }

  if (err.status === 409 && context === 'create') {
    if (code === 'IDEMPOTENCY_IN_PROGRESS') {
      return make('Tu pago se está procesando. Espera unos segundos y vuelve a intentarlo.', 'retry');
    }
    if (code === 'IDEMPOTENCY_KEY_REUSED') {
      return make('Esta solicitud ya se usó con otros datos. Inicia un pago nuevo.', 'new-attempt');
    }
  }

  switch (err.status) {
    case 400:
      return context === 'create'
        ? make('Revisa los datos del pago: alguno no es válido.', 'fix-form')
        : make('El identificador del pago no es válido.', 'none');
    case 401:
      return make('Tu sesión no es válida o venció. Vuelve a iniciar sesión.', 'none');
    case 403:
      return make('No tienes permiso para realizar esta acción.', 'none');
    case 404:
      return context === 'get'
        ? make('No encontramos este pago. Verifica el identificador.', 'none')
        : make('No pudimos completar la operación. Inténtalo de nuevo más tarde.', 'none');
    case 409:
      return make(
        'La operación entra en conflicto con otra ya registrada. Verifica el estado antes de volver a intentar.',
        'none',
      );
    default:
      break;
  }

  if (err.status >= 500) {
    return context === 'create'
      ? make(
          'El servicio no está disponible por ahora. Puedes reintentar: se usará la misma solicitud para evitar cobros duplicados.',
          'retry',
        )
      : make('El servicio no está disponible por ahora. Inténtalo en unos minutos.', 'retry');
  }

  return make(GENERIC, 'none');
}
```

```ts
// payments.service.ts
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { mapHttpError } from './api-error.mapper';
import { CreatePaymentRequest, CreatePaymentResult, PaymentResponse } from './payment.models';

@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /** POST /payments con Idempotency-Key obligatoria. 201 = nuevo, 200 = repetición idempotente. */
  createPayment(body: CreatePaymentRequest, idempotencyKey: string): Observable<CreatePaymentResult> {
    return this.http
      .post<PaymentResponse>(`${this.baseUrl}/payments`, body, {
        headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
        observe: 'response',
      })
      .pipe(
        map((res) => ({ payment: res.body as PaymentResponse, replayed: res.status === 200 })),
        catchError((err) => throwError(() => mapHttpError(err, 'create'))),
      );
  }

  /** GET /payments/{paymentId}. */
  getPayment(paymentId: string): Observable<PaymentResponse> {
    return this.http
      .get<PaymentResponse>(`${this.baseUrl}/payments/${encodeURIComponent(paymentId)}`)
      .pipe(catchError((err) => throwError(() => mapHttpError(err, 'get'))));
  }
}
```

```ts
// payment-create.component.ts
import { DatePipe } from '@angular/common';
import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { generateIdempotencyKey } from './idempotency-key';
import { formatCents, toCents } from './money';
import { CreatePaymentRequest, CreatePaymentResult, UserFacingError } from './payment.models';
import { statusInfo } from './payment-status';
import { PaymentsService } from './payments.service';

/** Validación de apoyo. El servidor valida de nuevo y manda. */
export function amountValidator(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  if (value === '') {
    return null; // lo cubre Validators.required
  }
  const cents = toCents(value);
  if (cents === null) {
    return { amountFormat: true };
  }
  if (cents <= 0) {
    return { amountPositive: true };
  }
  return null;
}

@Component({
  selector: 'app-payment-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './payment-create.component.html',
  styleUrl: './payment-create.component.scss',
})
export class PaymentCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly payments = inject(PaymentsService);

  /** Opciones conocidas por el mock (Issue). El nombre del campo es SUPUESTO (S1). */
  readonly payees = [
    { value: 'merchant-demo', label: 'Comercio demo' },
    { value: 'contact-demo', label: 'Contacto demo' },
  ];

  readonly form = this.fb.nonNullable.group({
    payeeId: ['', [Validators.required]],
    amount: ['', [Validators.required, amountValidator]],
  });

  readonly loading = signal(false);
  readonly error = signal<UserFacingError | null>(null);
  readonly result = signal<CreatePaymentResult | null>(null);
  submitted = false;

  /** Clave y cuerpo del intento en curso. Solo en memoria. */
  private attemptKey: string | null = null;
  private attemptPayload: string | null = null;

  @ViewChild('payeeField') payeeField?: ElementRef<HTMLSelectElement>;
  @ViewChild('amountField') amountField?: ElementRef<HTMLInputElement>;
  @ViewChild('errorBox') errorBox?: ElementRef<HTMLElement>;
  @ViewChild('resultTitle') resultTitle?: ElementRef<HTMLElement>;

  get payeeError(): string | null {
    const c = this.form.controls.payeeId;
    if (!(c.touched || this.submitted) || !c.errors) {
      return null;
    }
    return 'Selecciona un destinatario.';
  }

  get amountError(): string | null {
    const c = this.form.controls.amount;
    if (!(c.touched || this.submitted) || !c.errors) {
      return null;
    }
    if (c.errors['required']) {
      return 'Ingresa el monto.';
    }
    if (c.errors['amountFormat']) {
      return 'Usa solo números con hasta 2 decimales. Ejemplo: 12.50';
    }
    if (c.errors['amountPositive']) {
      return 'El monto debe ser mayor que S/ 0.00.';
    }
    return null;
  }

  submit(): void {
    if (this.loading()) {
      return;
    }
    this.submitted = true;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusFirstInvalid();
      return;
    }

    const payload = this.buildPayload();
    const serialized = JSON.stringify(payload);

    // Misma solicitud => misma clave (reintento seguro). Datos distintos => clave nueva.
    if (this.attemptKey === null || this.attemptPayload !== serialized) {
      this.attemptKey = generateIdempotencyKey();
      this.attemptPayload = serialized;
    }

    this.loading.set(true);
    this.error.set(null);

    this.payments
      .createPayment(payload, this.attemptKey)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.resetAttempt();
          this.focusLater(() => this.resultTitle?.nativeElement);
        },
        error: (err: UserFacingError) => {
          this.error.set(err);
          this.focusLater(() => this.errorBox?.nativeElement);
        },
      });
  }

  /** Descarta la clave actual: el próximo envío será un pago nuevo. */
  startNewPayment(): void {
    this.resetAttempt();
    this.error.set(null);
    this.result.set(null);
    this.submitted = false;
    this.form.reset({ payeeId: '', amount: '' });
    this.focusLater(() => this.payeeField?.nativeElement);
  }

  /** Para el error 'new-attempt': conserva los datos pero genera clave nueva. */
  discardAttempt(): void {
    this.resetAttempt();
    this.error.set(null);
    this.focusLater(() => this.amountField?.nativeElement);
  }

  statusLabel(status: string): string {
    return statusInfo(status).label;
  }

  statusDescription(status: string): string {
    return statusInfo(status).description;
  }

  money(cents: number, currency: string): string {
    return formatCents(cents, currency || 'PEN');
  }

  private buildPayload(): CreatePaymentRequest {
    const { payeeId, amount } = this.form.getRawValue();
    return { amountCents: toCents(amount) as number, currency: 'PEN', payeeId };
  }

  private resetAttempt(): void {
    this.attemptKey = null;
    this.attemptPayload = null;
  }

  private focusFirstInvalid(): void {
    if (this.form.controls.payeeId.invalid) {
      this.payeeField?.nativeElement.focus();
    } else if (this.form.controls.amount.invalid) {
      this.amountField?.nativeElement.focus();
    }
  }

  private focusLater(target: () => HTMLElement | undefined): void {
    setTimeout(() => target()?.focus(), 0);
  }
}
```

```html
<!-- payment-create.component.html -->
<main class="page">
  <h1>Nuevo pago</h1>

  @if (result(); as res) {
    <section class="panel panel--ok" aria-labelledby="result-title">
      <h2 id="result-title" #resultTitle tabindex="-1">Resultado del pago</h2>
      @if (res.replayed) {
        <p>Este pago ya estaba registrado. Te mostramos el resultado original.</p>
      }
      <dl>
        <dt>Estado</dt>
        <dd><strong>{{ statusLabel(res.payment.status) }}</strong></dd>
        <dt>Monto</dt>
        <dd>{{ money(res.payment.amountCents, res.payment.currency) }}</dd>
        <dt>Identificador</dt>
        <dd>{{ res.payment.paymentId }}</dd>
        @if (res.payment.createdAt) {
          <dt>Fecha</dt>
          <dd>{{ res.payment.createdAt | date: 'dd/MM/yyyy HH:mm' }}</dd>
        }
      </dl>
      <p>{{ statusDescription(res.payment.status) }}</p>
      <div class="actions">
        <a class="btn" [routerLink]="['/pagos', res.payment.paymentId]">Ver detalle</a>
        <button type="button" class="btn btn--secondary" (click)="startNewPayment()">Hacer otro pago</button>
      </div>
    </section>
  } @else {
    <p class="hint">Todavía no has enviado ningún pago en esta sesión.</p>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate [attr.aria-busy]="loading()">
      <div class="field">
        <label for="payeeId">Destinatario</label>
        <select
          id="payeeId"
          #payeeField
          formControlName="payeeId"
          required
          [attr.aria-invalid]="payeeError ? 'true' : null"
          [attr.aria-describedby]="payeeError ? 'payeeId-error' : null"
        >
          <option value="">Selecciona…</option>
          @for (p of payees; track p.value) {
            <option [value]="p.value">{{ p.label }}</option>
          }
        </select>
        @if (payeeError) {
          <p id="payeeId-error" class="error" role="alert">{{ payeeError }}</p>
        }
      </div>

      <div class="field">
        <label for="amount">Monto en soles (S/)</label>
        <input
          id="amount"
          #amountField
          type="text"
          inputmode="decimal"
          autocomplete="off"
          formControlName="amount"
          required
          aria-describedby="amount-help amount-error"
          [attr.aria-invalid]="amountError ? 'true' : null"
        />
        <p id="amount-help" class="help">Hasta 2 decimales. Ejemplo: 12.50. Moneda: Soles (PEN).</p>
        <div id="amount-error">
          @if (amountError) {
            <p class="error" role="alert">{{ amountError }}</p>
          }
        </div>
      </div>

      @if (loading()) {
        <p class="progress" role="status">Procesando pago…</p>
      }

      @if (error(); as err) {
        <div class="panel panel--error" role="alert" #errorBox tabindex="-1">
          <p>{{ err.message }}</p>
          @if (err.traceId) {
            <p class="help">Código de referencia: {{ err.traceId }}</p>
          }
          @if (err.action === 'retry') {
            <button type="button" class="btn btn--secondary" (click)="submit()" [disabled]="loading()">Reintentar</button>
          }
          @if (err.action === 'new-attempt') {
            <button type="button" class="btn btn--secondary" (click)="discardAttempt()">Iniciar un pago nuevo</button>
          }
        </div>
      }

      <button type="submit" class="btn" [disabled]="loading()">
        {{ loading() ? 'Procesando…' : 'Pagar' }}
      </button>
    </form>
  }
</main>
```

```scss
/* payment-create.component.scss */
.page { max-width: 32rem; margin: 0 auto; padding: 1rem; color: #1a1a1a; }
.field { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.25rem; }
label { font-weight: 600; }
input, select { padding: 0.6rem; font-size: 1rem; border: 2px solid #4a4a4a; border-radius: 4px; }
input[aria-invalid='true'], select[aria-invalid='true'] { border-color: #9b1c1c; }
.help, .hint { color: #4a4a4a; font-size: 0.9rem; margin: 0; }
.error { color: #9b1c1c; font-size: 0.9rem; margin: 0; }
.progress { color: #1a1a1a; font-weight: 600; }
.panel { border: 2px solid; border-radius: 6px; padding: 1rem; margin: 1rem 0; }
.panel--error { border-color: #9b1c1c; background: #fdf0f0; }
.panel--ok { border-color: #1b5e20; background: #f1f8f2; }
.actions { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.btn {
  display: inline-block; padding: 0.7rem 1.2rem; font-size: 1rem; border: 2px solid #0b3d91;
  background: #0b3d91; color: #fff; border-radius: 4px; cursor: pointer; text-decoration: none;
}
.btn--secondary { background: #fff; color: #0b3d91; }
.btn[disabled] { opacity: 0.7; cursor: not-allowed; }
:focus-visible { outline: 3px solid #ffbf47; outline-offset: 2px; }
```

```ts
// payment-detail.component.ts
import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { formatCents } from './money';
import { PaymentResponse, UserFacingError } from './payment.models';
import { statusInfo } from './payment-status';
import { PaymentsService } from './payments.service';

@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <main class="page">
      <h1 #title tabindex="-1">Detalle del pago</h1>

      @if (missingId()) {
        <p role="alert">Falta el identificador del pago.</p>
      } @else {
        @if (loading()) {
          <p role="status">Cargando pago…</p>
        }

        @if (error(); as err) {
          <div class="panel" role="alert" #errorBox tabindex="-1">
            <p>{{ err.message }}</p>
            @if (err.traceId) {
              <p class="help">Código de referencia: {{ err.traceId }}</p>
            }
            @if (err.action === 'retry') {
              <button type="button" class="btn" (click)="load()" [disabled]="loading()">Reintentar</button>
            }
          </div>
        }

        @if (payment(); as p) {
          <section aria-labelledby="estado">
            <h2 id="estado">Estado: {{ label(p.status) }}</h2>
            <p>{{ description(p.status) }}</p>
            <dl>
              <dt>Monto</dt>
              <dd>{{ money(p.amountCents, p.currency) }}</dd>
              <dt>Identificador</dt>
              <dd>{{ p.paymentId }}</dd>
              @if (p.createdAt) {
                <dt>Fecha</dt>
                <dd>{{ p.createdAt | date: 'dd/MM/yyyy HH:mm' }}</dd>
              }
            </dl>
            <button type="button" class="btn" (click)="load()" [disabled]="loading()">Actualizar estado</button>
          </section>
        }
      }

      <p><a routerLink="/pagos/nuevo">Hacer un pago nuevo</a></p>
    </main>
  `,
  styles: [
    `
      .page { max-width: 32rem; margin: 0 auto; padding: 1rem; color: #1a1a1a; }
      .panel { border: 2px solid #9b1c1c; background: #fdf0f0; border-radius: 6px; padding: 1rem; margin: 1rem 0; }
      .help { color: #4a4a4a; font-size: 0.9rem; }
      .btn { padding: 0.7rem 1.2rem; font-size: 1rem; border: 2px solid #0b3d91; background: #0b3d91; color: #fff; border-radius: 4px; cursor: pointer; }
      .btn[disabled] { opacity: 0.7; cursor: not-allowed; }
      :focus-visible { outline: 3px solid #ffbf47; outline-offset: 2px; }
    `,
  ],
})
export class PaymentDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly payments = inject(PaymentsService);

  readonly loading = signal(false);
  readonly error = signal<UserFacingError | null>(null);
  readonly payment = signal<PaymentResponse | null>(null);
  readonly missingId = signal(false);

  @ViewChild('errorBox') errorBox?: ElementRef<HTMLElement>;

  private paymentId = '';

  ngOnInit(): void {
    this.paymentId = (this.route.snapshot.paramMap.get('paymentId') ?? '').trim();
    if (!this.paymentId) {
      this.missingId.set(true);
      return;
    }
    this.load();
  }

  load(): void {
    if (this.loading() || !this.paymentId) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.payments
      .getPayment(this.paymentId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => this.payment.set(p),
        error: (err: UserFacingError) => {
          this.payment.set(null);
          this.error.set(err);
          setTimeout(() => this.errorBox?.nativeElement.focus(), 0);
        },
      });
  }

  label(status: string): string {
    return statusInfo(status).label;
  }

  description(status: string): string {
    return statusInfo(status).description;
  }

  money(cents: number, currency: string): string {
    return formatCents(cents, currency || 'PEN');
  }
}
```

```ts
// app.routes.ts
import { Routes } from '@angular/router';
import { PaymentCreateComponent } from './payment-create.component';
import { PaymentDetailComponent } from './payment-detail.component';

// Rutas SUPUESTAS (S9).
export const routes: Routes = [
  { path: 'pagos/nuevo', component: PaymentCreateComponent, title: 'Nuevo pago' },
  { path: 'pagos/:paymentId', component: PaymentDetailComponent, title: 'Detalle del pago' },
  { path: '', pathMatch: 'full', redirectTo: 'pagos/nuevo' },
];
```

```ts
// app.config.ts
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

// No se registra ningún interceptor de autenticación: el contrato recibido no define
// autenticación (S4). No se guardan tokens en el cliente.
export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideHttpClient()],
};
```

```ts
// api-error.mapper.spec.ts
import { HttpErrorResponse } from '@angular/common/http';
import { mapHttpError } from './api-error.mapper';

describe('mapHttpError', () => {
  const err = (status: number, body?: unknown) => new HttpErrorResponse({ status, error: body });

  it('red caída (0) -> reintentar', () => {
    const r = mapHttpError(err(0), 'create');
    expect(r.action).toBe('retry');
  });

  it('409 IDEMPOTENCY_IN_PROGRESS -> reintentar con la misma clave', () => {
    const r = mapHttpError(err(409, { code: 'IDEMPOTENCY_IN_PROGRESS', traceId: 't-1' }), 'create');
    expect(r.action).toBe('retry');
    expect(r.traceId).toBe('t-1');
  });

  it('409 IDEMPOTENCY_KEY_REUSED -> pago nuevo', () => {
    const r = mapHttpError(err(409, { code: 'IDEMPOTENCY_KEY_REUSED' }), 'create');
    expect(r.action).toBe('new-attempt');
  });

  it('400 al crear -> corregir formulario', () => {
    expect(mapHttpError(err(400, { code: 'X' }), 'create').action).toBe('fix-form');
  });

  it('404 al consultar -> mensaje de no encontrado', () => {
    expect(mapHttpError(err(404), 'get').message).toContain('No encontramos');
  });

  it('5xx -> reintentar y no expone el message del servidor', () => {
    const r = mapHttpError(err(500, { message: 'SQL error en tabla payments' }), 'create');
    expect(r.action).toBe('retry');
    expect(r.message).not.toContain('SQL');
  });

  it('401 y 403 -> mensajes genéricos sin acción', () => {
    expect(mapHttpError(err(401), 'create').action).toBe('none');
    expect(mapHttpError(err(403), 'create').action).toBe('none');
  });
});
```

```ts
// payments.service.spec.ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserFacingError } from './payment.models';
import { PaymentsService } from './payments.service';

describe('PaymentsService', () => {
  let service: PaymentsService;
  let http: HttpTestingController;
  const body = { amountCents: 1250, currency: 'PEN' as const, payeeId: 'merchant-demo' };
  const payment = { paymentId: 'p1', status: 'PENDING', amountCents: 1250, currency: 'PEN' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PaymentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envía el header Idempotency-Key y marca replayed=false en 201', () => {
    let replayed: boolean | undefined;
    service.createPayment(body, 'pay-12345678').subscribe((r) => (replayed = r.replayed));
    const req = http.expectOne('/api/v1/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('pay-12345678');
    req.flush(payment, { status: 201, statusText: 'Created' });
    expect(replayed).toBeFalse();
  });

  it('marca replayed=true en 200', () => {
    let replayed: boolean | undefined;
    service.createPayment(body, 'pay-12345678').subscribe((r) => (replayed = r.replayed));
    http.expectOne('/api/v1/payments').flush(payment, { status: 200, statusText: 'OK' });
    expect(replayed).toBeTrue();
  });

  it('traduce un 404 al consultar', () => {
    let error: UserFacingError | undefined;
    service.getPayment('nope').subscribe({ error: (e) => (error = e) });
    http.expectOne('/api/v1/payments/nope').flush({ code: 'PAYMENT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(error?.message).toContain('No encontramos');
  });
});
```

```ts
// payment-create.component.spec.ts
import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PaymentCreateComponent } from './payment-create.component';
import { UserFacingError } from './payment.models';
import { PaymentsService } from './payments.service';

describe('PaymentCreateComponent', () => {
  let fixture: ComponentFixture<PaymentCreateComponent>;
  let component: PaymentCreateComponent;
  let service: jasmine.SpyObj<PaymentsService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<PaymentsService>('PaymentsService', ['createPayment']);
    await TestBed.configureTestingModule({
      imports: [PaymentCreateComponent],
      providers: [{ provide: PaymentsService, useValue: service }, provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(PaymentCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('no llama al servicio si el formulario es inválido', () => {
    component.submit();
    expect(service.createPayment).not.toHaveBeenCalled();
  });

  it('convierte el monto a céntimos y fija PEN', () => {
    service.createPayment.and.returnValue(
      of({ payment: { paymentId: 'p1', status: 'PENDING', amountCents: 1250, currency: 'PEN' }, replayed: false }),
    );
    component.form.setValue({ payeeId: 'merchant-demo', amount: '12,50' });
    component.submit();
    const [payload] = service.createPayment.calls.mostRecent().args;
    expect(payload).toEqual({ amountCents: 1250, currency: 'PEN', payeeId: 'merchant-demo' });
  });

  it('reutiliza la misma clave al reintentar con los mismos datos', () => {
    const fail: UserFacingError = { message: 'x', action: 'retry' };
    service.createPayment.and.returnValue(throwError(() => fail));
    component.form.setValue({ payeeId: 'merchant-demo', amount: '10' });
    component.submit();
    component.submit();
    const keys = service.createPayment.calls.allArgs().map((a) => a[1]);
    expect(keys.length).toBe(2);
    expect(keys[0]).toBe(keys[1]);
  });

  it('genera una clave nueva si cambian los datos', () => {
    const fail: UserFacingError = { message: 'x', action: 'retry' };
    service.createPayment.and.returnValue(throwError(() => fail));
    component.form.setValue({ payeeId: 'merchant-demo', amount: '10' });
    component.submit();
    component.form.setValue({ payeeId: 'merchant-demo', amount: '11' });
    component.submit();
    const keys = service.createPayment.calls.allArgs().map((a) => a[1]);
    expect(keys[0]).not.toBe(keys[1]);
  });

  it('la clave generada cumple el patrón del servidor', () => {
    service.createPayment.and.returnValue(throwError(() => ({ message: 'x', action: 'none' }) as UserFacingError));
    component.form.setValue({ payeeId: 'contact-demo', amount: '5' });
    component.submit();
    const key = service.createPayment.calls.mostRecent().args[1];
    expect(key).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
  });
});
```

## Resumen final

- **Entregado:** `frontend_spec.md` y el código del corte vertical de pagos, con idempotencia, estados de carga, vacío y error, y accesibilidad.
- **NO EJECUTADO:** compilación, pruebas y verificación manual.
- **Pendiente de otros roles:** S1 a S10, sobre todo el cuerpo exacto de `POST /payments`, la lista completa de códigos de error, la autenticación y la URL base del BFF.