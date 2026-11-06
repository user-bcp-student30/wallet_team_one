# frontend_spec.md

> **Estado de verificación:** no ejecuté `ng build`, `ng test`, `ng lint` ni ninguna otra herramienta, y no probé nada en navegador ni con lector de pantalla. Todo lo que dice "pasa", "compila" o "cumple" es **NO EJECUTADO**. Las pruebas son propuestas escritas, no resultados.

## 0) Alcance y base usada

**Contrato disponible.** El contrato de Arquitectura llega truncado después de `E1 … 201 Created`. Usé solo lo visible:

- `POST /onboarding/sessions` (E1), con `Idempotency-Key` obligatorio y `acquisitionChannel` opcional de máximo 50 caracteres.
- El modelo de progreso (§3.2).
- Los encabezados generales y el catálogo provisional de pasos.
- De la implementación de Backend: el cuerpo de error `{ code, message, correlationId, fieldErrors[{field,message}] }` y el éxito de E1 con 201 (creada) o 200 (reanudada).

**Lo que NO implementé** porque no está en el contrato visible. No inventé rutas, métodos ni cuerpos para:

- consultar el progreso (GET);
- enviar un paso (que dispara `POST /kyc/verifications`);
- completar el onboarding;
- el reporte mensual (es de Datos, no de la UI).

`OnboardingService.submitStep()` existe como punto de extensión y devuelve un error controlado "no disponible" hasta que Arquitectura y Backend entreguen el contrato (SP-1).

**Supuestos técnicos del cliente.**

- Angular 17 o superior (componentes standalone, `@if`/`@for`, signals).
- `HttpClient` provisto con `provideHttpClient(...)`.
- La autenticación (`Authorization: Bearer`) la añade un interceptor que ya existe en la app. No guardo ni leo tokens aquí (SP-2).

## 1) Análisis previo: campos, acciones y estados

### 1.1 Recorrido

1. El usuario entra a la pantalla de onboarding. El componente llama a E1, que inicia o reanuda la sesión.
2. Se muestran el indicador de pasos (con "Paso X de N") y el formulario del paso actual.
3. El usuario pulsa **Continuar**. Si hay errores del cliente, el avance se bloquea y se muestran mensajes accionables. Si no, se envía el paso (pendiente SP-1) y se vuelve a renderizar con la respuesta del servidor.
4. Con `status=COMPLETED`, `walletStatus=ACTIVE` y `nextAction=GO_TO_LOW_AMOUNT_PAYMENTS`, aparece el botón "Ir a pagos de bajo monto".

### 1.2 Campos

| Campo | Origen | Validación visible | Estado |
|---|---|---|---|
| `acquisitionChannel` | Entrada del componente (`@Input`), no es un campo visible | Se recorta con `trim`. Máximo 50 caracteres, validado por el servidor (400). | Contrato E1. De dónde sale el valor es SP-3. |
| Campos de `PERSONAL_DATA`, `IDENTITY_DOCUMENT`, `CONFIRMATION` | Token `ONBOARDING_STEP_FIELDS` (vacío por defecto) | Se generan desde la definición: obligatorio, longitud mínima y máxima, patrón. | **SUPUESTO PENDIENTE (SP-4):** el contrato no define los campos. |

No inventé campos de KYC. El componente construye el formulario a partir de la definición inyectada y, si no hay definición para el paso, muestra "Este paso todavía no está disponible" y deshabilita **Continuar**.

### 1.3 Acciones

| Acción | Elemento | Efecto |
|---|---|---|
| Cargar o reanudar | `ngOnInit` → `service.start()` | E1 |
| Continuar | botón `type=submit` | Valida en el cliente y llama a `submitStep` (SP-1) |
| Reintentar | botón en el bloque de error | Repite E1 con la misma `Idempotency-Key` si falló por red o 5xx |
| Empezar de nuevo | botón en `CLOSED_INCOMPLETE` | E1 con una clave nueva |
| Iniciar sesión | botón en 401 | Navega a `routes.login` si está configurada (SP-2) |
| Ir a pagos de bajo monto | botón en el estado completado | `router.navigateByUrl` con la ruta de la configuración local, **no** del servidor |

### 1.4 Estados de pantalla

`loading` · `ready` (con indicador y formulario) · `empty` (sin pasos) · `closed` (`CLOSED_INCOMPLETE`) · `activating` (`COMPLETED` con wallet `PENDING`) · `completed` (`COMPLETED` y wallet `ACTIVE`) · `error`.

### 1.5 Relación con los criterios de aceptación

| # | Criterio | Qué hace la UI |
|---|---|---|
| 1 | Secuencia de pasos y paso actual siempre visibles | Indicador `<ol>` con `aria-current="step"` y "Paso X de N", calculado desde `steps[].status` (no desde `currentStepIndex`, ver SP-5). |
| 2 | Bloqueo y mensaje accionable | Validadores reactivos, resumen de errores con `role="alert"` y errores por campo del servidor (`fieldErrors`). |
| 3 | Wallet activa y acceso a pagos | Estado `completed` con botón de navegación. |
| 4 | Eventos analíticos | **Lo emite el servidor** (timestamps del servidor). La UI no emite eventos y no inventa eventos de cliente. |
| 5 | Mediana 8 → 3 min | Resultado de negocio, fuera del alcance de la UI. |
| 6 | MAU y CAC | Es de Datos (#14), fuera del alcance de la UI. |

## 2) Estados: carga, vacío y error

| Estado | Comportamiento |
|---|---|
| Carga | `role="status"` con spinner (decorativo, `aria-hidden`) y texto "Cargando tu registro…". Al enviar, **Continuar** queda deshabilitado, con `aria-busy` y texto "Enviando…". |
| Vacío | Si `steps` llega vacío: "No hay pasos disponibles por ahora" y botón **Reintentar**. |
| Error | Bloque `role="alert"` con título, mensaje, referencia opcional (`correlationId`) y acciones según el tipo. |

## 3) Mapeo de errores de API a mensajes

El mapeo se hace **por código HTTP**. Los valores del campo `code` del cuerpo de error no están en el contrato, así que no dependo de ellos (SP-6). Nunca se muestran trazas, SQL ni el cuerpo crudo. Solo se muestran `fieldErrors[].message` (recortados a 200 caracteres) y el `correlationId` saneado.

| HTTP | Título | Mensaje | Acción |
|---|---|---|---|
| 0 (red) | Sin conexión | No pudimos conectarnos. Revisa tu internet e inténtalo de nuevo. | Reintentar |
| 400 | Revisa los datos | Hay datos incompletos o inválidos. Corrígelos y vuelve a intentarlo. (En E1: "No pudimos iniciar tu registro. Vuelve a abrir la app e inténtalo de nuevo.") | Corregir. Errores por campo en el formulario. |
| 401 | Tu sesión venció | Inicia sesión de nuevo para continuar con tu registro. | Iniciar sesión |
| 403 | Sin permiso | No tienes permiso para continuar con este registro. | Ninguna |
| 404 | Registro no encontrado | No encontramos tu registro. Puedes empezar de nuevo. | Reintentar |
| 409 | Tu registro cambió | Tu avance se actualizó desde otro dispositivo o ya se había enviado. Actualiza para ver el estado actual. | Actualizar |
| 5xx | Problema temporal | Tuvimos un problema de nuestro lado. Inténtalo de nuevo en unos minutos. | Reintentar |
| Otro | No se pudo completar | No pudimos completar la acción. Inténtalo de nuevo. | Reintentar |

## 4) Accesibilidad

- Cada `input` tiene un `<label for>` asociado. Los obligatorios llevan "(obligatorio)" para lector de pantalla y `aria-required`.
- Los errores se anuncian con `role="alert"`. Los errores inline están enlazados con `aria-describedby` y `aria-invalid`, y no son live, para no duplicar el anuncio.
- Al cambiar de paso o de estado, el foco va al encabezado (`tabindex="-1"`). Al haber error, el foco va al bloque de error.
- El resumen de errores contiene botones que llevan el foco al campo.
- El indicador de pasos usa `<nav aria-label>` y `<ol>`. El estado no depende solo del color: lleva marcador ✓ o número y texto oculto visualmente ("completado", "paso actual", "pendiente").
- Todo es operable por teclado, con `:focus-visible` visible. Se respeta `prefers-reduced-motion`.
- Contraste: los colores elegidos (#1a1a1a, #b00020, #0b57d0 sobre blanco) están pensados para AA, pero **no medí** el contraste (NO EJECUTADO). Verificar con una herramienta.

## 5) Seguridad

- La validación del cliente solo mejora la experiencia. El servidor valida y autoriza.
- No guardo tokens ni secretos. No uso `localStorage`, `sessionStorage` ni `innerHTML`: todo se interpola como texto.
- El destino de navegación sale de una tabla local de configuración. Nunca se navega a un `target` enviado por el servidor (evita redirecciones abiertas).
- La `Idempotency-Key` es un UUID que vive solo en memoria. Se reutiliza en reintentos por red o 5xx y se descarta tras un éxito o un 4xx.

## 6) Supuestos pendientes

| ID | Supuesto | Quién define |
|---|---|---|
| SP-1 | Contrato de enviar paso, completar y consultar progreso (rutas, métodos, cuerpo, `version`, errores). `submitStep` no está implementado. | Arquitectura y Backend |
| SP-2 | Mecanismo de autenticación (interceptor existente), ruta de login y URL base del BFF (`apiBaseUrl`). | Arquitectura |
| SP-3 | Origen de `acquisitionChannel` (parámetro de campaña, app, otro). | Producto y Datos |
| SP-4 | Campos, tipos y validaciones de cada paso, y requisitos de KYC y documentación. | Cumplimiento y Producto |
| SP-5 | Si `currentStepIndex` es base 0 o 1 (el ejemplo muestra `2` con `IDENTITY_DOCUMENT`, el segundo paso). La UI no lo usa. | Arquitectura |
| SP-6 | Valores posibles de `ErrorResponse.code`. | Backend |
| SP-7 | Ruta real de `LOW_AMOUNT_PAYMENTS_ENTRY` en la app. | Frontend con Producto |
| SP-8 | Qué hacer con una sesión `COMPLETED` y wallet `PENDING` (no hay endpoint para refrescar). | Backend |
| SP-9 | Si E1 acepta `{}` como cuerpo "vacío". Envío `{}` con `Content-Type: application/json`. | Backend |

## 7) Evidencia por criterio y pruebas

Todo en estado **NO EJECUTADO**.

| # | Archivo y elemento | Prueba propuesta |
|---|---|---|
| 1 | `onboarding.component.html`: `nav > ol`, `aria-current`, "Paso X de N" | `onboarding.component.spec.ts` ("marca el paso actual"). Manual: recargar a mitad del flujo y verificar que el paso se conserva. |
| 2 | `onboarding.component.ts` (`onContinue`, validadores), bloque de resumen `role="alert"` | `spec` ("bloquea el avance si hay campos inválidos"). Manual: dejar un campo obligatorio vacío, pulsar Continuar y verificar que no hay petición. |
| 3 | `onboarding.component.html`: bloque `completed`, botón `goToPayments()` | `spec` ("navega al punto de entrada configurado"). Manual con un backend que devuelva `COMPLETED` y wallet `ACTIVE`. |
| 4 | Servidor (Backend). La UI no interviene. | N/A en frontend. Verificar en pruebas de Backend. |
| 5, 6 | Fuera del alcance de la UI | N/A |
| Errores | `onboarding-error.mapper.ts` | `onboarding-error.mapper.spec.ts` |
| Idempotencia | `onboarding.service.ts` | `onboarding.service.spec.ts` |

Comandos propuestos: `ng test --watch=false`, `ng build`, `ng lint` (si está configurado). Manual: Chrome DevTools con red simulada (Offline y Slow 3G), navegación solo con teclado y NVDA o VoiceOver.

## 8) Archivos entregados

`onboarding.models.ts`, `onboarding.config.ts`, `onboarding-error.mapper.ts`, `onboarding.service.ts`, `onboarding.component.ts`, `onboarding.component.html`, `onboarding.component.scss`, `onboarding-error.mapper.spec.ts`, `onboarding.service.spec.ts`, `onboarding.component.spec.ts`.

Montaje: registrar la ruta con `loadComponent` en el enrutador de la app. La ruta (`/onboarding`) es decisión de Arquitectura y la app.

---

```ts
// onboarding.models.ts
export type OnboardingStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED_INCOMPLETE';
export type StepStatus = 'COMPLETED' | 'CURRENT' | 'PENDING';
export type WalletStatus = 'PENDING' | 'ACTIVE';
export type NextActionType = 'CONTINUE_STEP' | 'GO_TO_LOW_AMOUNT_PAYMENTS';

export interface OnboardingStep {
  stepId: string;
  order: number;
  label: string;
  required: boolean;
  status: StepStatus;
}

export interface NextAction {
  type: NextActionType;
  target: string;
}

/** Modelo de progreso del contrato (§3.2). */
export interface OnboardingProgress {
  sessionId: string;
  status: OnboardingStatus;
  totalSteps: number;
  currentStepIndex: number; // base 0 o 1 sin confirmar (SP-5): la UI no lo usa.
  currentStepId: string;
  steps: OnboardingStep[];
  version: number;
  walletStatus: WalletStatus;
  nextAction: NextAction;
}

export interface StartOnboardingRequest {
  acquisitionChannel?: string;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

/** Cuerpo de error devuelto por el BFF (según la implementación de Backend). */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  correlationId?: string;
  fieldErrors?: ApiFieldError[];
}

export type ErrorContext = 'start' | 'submit';

/** Error ya traducido, seguro para mostrar al usuario. */
export interface UiError {
  status: number;
  title: string;
  message: string;
  correlationId?: string;
  fieldErrors: ApiFieldError[];
  retryable: boolean;
  requiresLogin: boolean;
  refreshRequired: boolean;
}

/** Definición de un campo de paso (SP-4: el contrato no define los campos). */
export interface StepFieldDefinition {
  name: string;
  label: string;
  type?: 'text' | 'tel' | 'email' | 'date';
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  hint?: string;
  autocomplete?: string;
}
```

```ts
// onboarding.config.ts
import { InjectionToken } from '@angular/core';
import { StepFieldDefinition } from './onboarding.models';

export interface OnboardingConfig {
  /** URL base del BFF. Vacío = mismo origen (SP-2). */
  apiBaseUrl: string;
  routes: {
    /** Ruta de login de la app (SP-2). Si no existe, no se muestra el botón. */
    login?: string;
    /** Mapa clave lógica de `nextAction.target` -> ruta de la app (SP-7). */
    targets: Record<string, string>;
  };
}

export const ONBOARDING_CONFIG = new InjectionToken<OnboardingConfig>('ONBOARDING_CONFIG', {
  providedIn: 'root',
  factory: () => ({ apiBaseUrl: '', routes: { targets: {} } }),
});

/** stepId -> campos del paso. Vacío por defecto: pendiente de Cumplimiento y Producto (SP-4). */
export const ONBOARDING_STEP_FIELDS = new InjectionToken<Record<string, StepFieldDefinition[]>>(
  'ONBOARDING_STEP_FIELDS',
  { providedIn: 'root', factory: () => ({}) },
);
```

```ts
// onboarding-error.mapper.ts
import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody, ApiFieldError, ErrorContext, UiError } from './onboarding.models';

const MAX_FIELD_ERRORS = 10;
const MAX_MESSAGE_LENGTH = 200;
const CORRELATION_ID_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;

function readBody(err: HttpErrorResponse): ApiErrorBody | null {
  return err.error && typeof err.error === 'object' ? (err.error as ApiErrorBody) : null;
}

function readFieldErrors(body: ApiErrorBody | null): ApiFieldError[] {
  if (!body || !Array.isArray(body.fieldErrors)) {
    return [];
  }
  return body.fieldErrors
    .filter((f) => f && typeof f.field === 'string' && typeof f.message === 'string')
    .slice(0, MAX_FIELD_ERRORS)
    .map((f) => ({ field: f.field, message: f.message.slice(0, MAX_MESSAGE_LENGTH) }));
}

function readCorrelationId(err: HttpErrorResponse, body: ApiErrorBody | null): string | undefined {
  const candidate = body?.correlationId ?? err.headers?.get('X-Correlation-Id') ?? undefined;
  return typeof candidate === 'string' && CORRELATION_ID_PATTERN.test(candidate) ? candidate : undefined;
}

/**
 * Traduce un error HTTP a un mensaje comprensible. Se decide por código HTTP:
 * los valores de `code` no están en el contrato (SP-6). No expone trazas ni el cuerpo crudo.
 */
export function toUiError(err: unknown, context: ErrorContext): UiError {
  const base: UiError = {
    status: -1,
    title: 'No se pudo completar la acción',
    message: 'No pudimos completar la acción. Inténtalo de nuevo.',
    fieldErrors: [],
    retryable: true,
    requiresLogin: false,
    refreshRequired: false,
  };

  if (!(err instanceof HttpErrorResponse)) {
    return base;
  }

  const body = readBody(err);
  const common = {
    ...base,
    status: err.status,
    correlationId: readCorrelationId(err, body),
    fieldErrors: readFieldErrors(body),
  };

  if (err.status === 0) {
    return {
      ...common,
      title: 'Sin conexión',
      message: 'No pudimos conectarnos. Revisa tu internet e inténtalo de nuevo.',
    };
  }

  switch (err.status) {
    case 400:
      return {
        ...common,
        title: 'Revisa los datos',
        message:
          context === 'start'
            ? 'No pudimos iniciar tu registro. Vuelve a abrir la app e inténtalo de nuevo.'
            : 'Hay datos incompletos o inválidos. Corrígelos y vuelve a intentarlo.',
        // En 'start' los errores por campo no son corregibles por el usuario.
        fieldErrors: context === 'start' ? [] : common.fieldErrors,
        retryable: context === 'start',
      };
    case 401:
      return {
        ...common,
        title: 'Tu sesión venció',
        message: 'Inicia sesión de nuevo para continuar con tu registro.',
        retryable: false,
        requiresLogin: true,
      };
    case 403:
      return {
        ...common,
        title: 'Sin permiso',
        message: 'No tienes permiso para continuar con este registro.',
        retryable: false,
      };
    case 404:
      return {
        ...common,
        title: 'Registro no encontrado',
        message: 'No encontramos tu registro. Puedes empezar de nuevo.',
      };
    case 409:
      return {
        ...common,
        title: 'Tu registro cambió',
        message:
          'Tu avance se actualizó desde otro dispositivo o ya se había enviado. Actualiza para ver el estado actual.',
        refreshRequired: true,
      };
    default:
      if (err.status >= 500) {
        return {
          ...common,
          title: 'Problema temporal',
          message: 'Tuvimos un problema de nuestro lado. Inténtalo de nuevo en unos minutos.',
        };
      }
      return common;
  }
}

export function notAvailableError(): UiError {
  return {
    status: -1,
    title: 'Paso no disponible',
    message: 'Esta función todavía no está disponible. Inténtalo más tarde.',
    fieldErrors: [],
    retryable: false,
    requiresLogin: false,
    refreshRequired: false,
  };
}
```

```ts
// onboarding.service.ts
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import { ONBOARDING_CONFIG } from './onboarding.config';
import { notAvailableError, toUiError } from './onboarding-error.mapper';
import { OnboardingProgress, StartOnboardingRequest } from './onboarding.models';

export function generateIdempotencyKey(): string {
  const c = globalThis.crypto;
  if (typeof c?.randomUUID === 'function') {
    return c.randomUUID();
  }
  const b = new Uint8Array(16);
  c.getRandomValues(b);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ONBOARDING_CONFIG);

  /** Clave en memoria: se reutiliza en reintentos por red/5xx y se descarta tras éxito o 4xx. */
  private startKey: string | null = null;

  /** E1: POST /onboarding/sessions. Inicia o reanuda el onboarding. */
  start(acquisitionChannel?: string): Observable<OnboardingProgress> {
    const key = this.startKey ?? (this.startKey = generateIdempotencyKey());
    const channel = acquisitionChannel?.trim();
    const body: StartOnboardingRequest = channel ? { acquisitionChannel: channel } : {};

    return this.http
      .post<OnboardingProgress>(`${this.config.apiBaseUrl}/onboarding/sessions`, body, {
        headers: new HttpHeaders({ 'Idempotency-Key': key }),
      })
      .pipe(
        map((p) => this.normalize(p)),
        tap(() => (this.startKey = null)),
        catchError((err: unknown) => {
          const ui = toUiError(err, 'start');
          if (ui.status >= 400 && ui.status < 500) {
            this.startKey = null;
          }
          return throwError(() => ui);
        }),
      );
  }

  /**
   * Enviar un paso: NO IMPLEMENTADO. El contrato visible no define ruta, método ni cuerpo (SP-1).
   * Devuelve un error controlado en lugar de inventar un endpoint.
   */
  submitStep(_progress: OnboardingProgress, _values: Record<string, string>): Observable<OnboardingProgress> {
    return throwError(() => notAvailableError());
  }

  private normalize(p: OnboardingProgress): OnboardingProgress {
    if (!p || !Array.isArray(p.steps)) {
      throw new Error('Respuesta de progreso inválida');
    }
    return { ...p, steps: [...p.steps].sort((a, b) => a.order - b.order) };
  }
}
```

```ts
// onboarding.component.ts
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Input, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { ONBOARDING_CONFIG, ONBOARDING_STEP_FIELDS } from './onboarding.config';
import { notAvailableError } from './onboarding-error.mapper';
import { OnboardingService } from './onboarding.service';
import { OnboardingProgress, OnboardingStep, StepFieldDefinition, StepStatus, UiError } from './onboarding.models';

interface SummaryItem {
  name?: string;
  message: string;
}

const notBlank: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  typeof c.value === 'string' && c.value.trim().length === 0 ? { required: true } : null;

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingComponent implements OnInit {
  private readonly service = inject(OnboardingService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly config = inject(ONBOARDING_CONFIG);
  private readonly fieldDefs = inject(ONBOARDING_STEP_FIELDS);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Origen pendiente de definir (SP-3). */
  @Input() acquisitionChannel?: string;

  readonly progress = signal<OnboardingProgress | null>(null);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly summary = signal<SummaryItem[]>([]);

  readonly stepForm = new FormGroup<Record<string, FormControl<string>>>({});

  readonly steps = computed<OnboardingStep[]>(() => this.progress()?.steps ?? []);
  readonly currentStep = computed(() => this.steps().find((s) => s.status === 'CURRENT') ?? null);
  readonly position = computed(() => {
    const i = this.steps().findIndex((s) => s.status === 'CURRENT');
    return i >= 0 ? i + 1 : 0;
  });
  readonly currentFields = computed<StepFieldDefinition[]>(() => {
    const step = this.currentStep();
    return step ? (this.fieldDefs[step.stepId] ?? []) : [];
  });

  readonly viewState = computed<'loading' | 'error' | 'empty' | 'closed' | 'activating' | 'completed' | 'ready'>(() => {
    const p = this.progress();
    if (this.loading() && !p) return 'loading';
    if (this.error() && !p) return 'error';
    if (!p) return 'loading';
    if (p.steps.length === 0) return 'empty';
    if (p.status === 'CLOSED_INCOMPLETE') return 'closed';
    if (p.status === 'COMPLETED') {
      return p.walletStatus === 'ACTIVE' && p.nextAction?.type === 'GO_TO_LOW_AMOUNT_PAYMENTS'
        ? 'completed'
        : 'activating';
    }
    return 'ready';
  });

  private submitAttempted = false;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.summary.set([]);
    this.service
      .start(this.acquisitionChannel)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (p) => this.applyProgress(p),
        error: (e: UiError | Error) => this.showError(this.asUiError(e)),
      });
  }

  onContinue(): void {
    if (this.submitting() || !this.progress()) return;
    this.submitAttempted = true;
    this.error.set(null);
    this.stepForm.markAllAsTouched();

    if (this.currentFields().length === 0) {
      this.showError(notAvailableError());
      return;
    }
    if (this.stepForm.invalid) {
      this.summary.set(this.buildSummary());
      this.focus('[data-summary-focus]');
      return;
    }

    this.summary.set([]);
    this.submitting.set(true);
    this.service
      .submitStep(this.progress()!, this.stepForm.getRawValue())
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (p) => this.applyProgress(p),
        error: (e: UiError | Error) => {
          const ui = this.asUiError(e);
          this.applyServerFieldErrors(ui);
          this.showError(ui);
        },
      });
  }

  goToPayments(): void {
    const target = this.progress()?.nextAction?.target;
    // La ruta sale de la configuración local, nunca del servidor (SP-7).
    const route = target ? this.config.routes.targets[target] : undefined;
    if (!route) {
      this.showError({
        ...notAvailableError(),
        title: 'No pudimos abrir pagos',
        message: 'Tu wallet está activa, pero no pudimos abrir la pantalla de pagos. Inténtalo más tarde.',
      });
      return;
    }
    void this.router.navigateByUrl(route);
  }

  goToLogin(): void {
    const login = this.config.routes.login;
    if (login) void this.router.navigateByUrl(login);
  }

  get canGoToLogin(): boolean {
    return !!this.config.routes.login;
  }

  statusText(status: StepStatus): string {
    return status === 'COMPLETED' ? 'completado' : status === 'CURRENT' ? 'paso actual' : 'pendiente';
  }

  errorText(name: string): string | null {
    const control = this.stepForm.get(name);
    const def = this.currentFields().find((f) => f.name === name);
    if (!control || !def || !control.errors || !(control.touched || this.submitAttempted)) return null;
    return this.messageFor(def, control.errors);
  }

  describedBy(f: StepFieldDefinition): string | null {
    const ids: string[] = [];
    if (f.hint) ids.push(`h-${f.name}`);
    if (this.errorText(f.name)) ids.push(`e-${f.name}`);
    return ids.length ? ids.join(' ') : null;
  }

  focusField(name: string | undefined): void {
    if (!name) return;
    this.host.nativeElement.querySelector<HTMLElement>(`#f-${CSS.escape(name)}`)?.focus();
  }

  private applyProgress(p: OnboardingProgress): void {
    this.progress.set(p);
    this.error.set(null);
    this.summary.set([]);
    this.submitAttempted = false;
    this.rebuildForm(p.steps.find((s) => s.status === 'CURRENT')?.stepId);
    this.focus('[data-focus]');
  }

  private rebuildForm(stepId: string | undefined): void {
    Object.keys(this.stepForm.controls).forEach((k) => this.stepForm.removeControl(k));
    const defs = stepId ? (this.fieldDefs[stepId] ?? []) : [];
    for (const f of defs) {
      this.stepForm.addControl(f.name, this.fb.control('', this.validatorsFor(f)));
    }
  }

  private validatorsFor(f: StepFieldDefinition): ValidatorFn[] {
    const v: ValidatorFn[] = [];
    if (f.required) v.push(Validators.required, notBlank);
    if (f.minLength) v.push(Validators.minLength(f.minLength));
    if (f.maxLength) v.push(Validators.maxLength(f.maxLength));
    if (f.pattern) v.push(Validators.pattern(f.pattern));
    return v;
  }

  private messageFor(def: StepFieldDefinition, errors: ValidationErrors): string {
    if (typeof errors['server'] === 'string') return errors['server'];
    if (errors['required']) return `Ingresa ${def.label.toLowerCase()} para continuar.`;
    if (errors['minlength']) return `${def.label} debe tener al menos ${errors['minlength'].requiredLength} caracteres.`;
    if (errors['maxlength']) return `${def.label} no puede superar ${errors['maxlength'].requiredLength} caracteres.`;
    if (errors['pattern']) return `${def.label} tiene un formato no válido. Revísalo e inténtalo de nuevo.`;
    return `${def.label} no es válido.`;
  }

  private buildSummary(): SummaryItem[] {
    const items: SummaryItem[] = [];
    for (const def of this.currentFields()) {
      const msg = this.errorText(def.name);
      if (msg) items.push({ name: def.name, message: msg });
    }
    return items;
  }

  /** Errores por campo del servidor: a su control si existe; si no, al resumen. */
  private applyServerFieldErrors(ui: UiError): void {
    const items: SummaryItem[] = [];
    for (const fe of ui.fieldErrors) {
      const control = this.stepForm.get(fe.field);
      if (control) {
        control.setErrors({ server: fe.message });
        control.markAsTouched();
        items.push({ name: fe.field, message: fe.message });
      } else {
        items.push({ message: fe.message });
      }
    }
    this.summary.set(items);
  }

  private showError(ui: UiError): void {
    this.error.set(ui);
    this.focus('[data-error-focus]');
  }

  private asUiError(e: UiError | Error): UiError {
    return 'title' in e && 'fieldErrors' in e
      ? (e as UiError)
      : {
          ...notAvailableError(),
          title: 'No se pudo completar la acción',
          message: 'No pudimos completar la acción. Inténtalo de nuevo.',
          retryable: true,
        };
  }

  private focus(selector: string): void {
    setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }
}
```

```html
<!-- onboarding.component.html -->
<main class="onb" aria-labelledby="onb-title">
  <h1 id="onb-title" class="onb__title">Activa tu wallet</h1>

  @if (viewState() === 'loading') {
    <div class="onb__loading" role="status">
      <span class="spinner" aria-hidden="true"></span>
      <span>Cargando tu registro…</span>
    </div>
  }

  @if (error(); as err) {
    <section class="onb__error" role="alert" tabindex="-1" data-error-focus>
      <h2 class="onb__error-title">{{ err.title }}</h2>
      <p>{{ err.message }}</p>
      @if (err.correlationId) {
        <p class="onb__ref">Código de referencia: {{ err.correlationId }}</p>
      }
      <div class="onb__actions">
        @if (err.requiresLogin && canGoToLogin) {
          <button type="button" class="btn" (click)="goToLogin()">Iniciar sesión</button>
        } @else if (err.refreshRequired) {
          <button type="button" class="btn" (click)="load()" [disabled]="loading()">Actualizar</button>
        } @else if (err.retryable && viewState() === 'error') {
          <button type="button" class="btn" (click)="load()" [disabled]="loading()">Reintentar</button>
        }
      </div>
    </section>
  }

  @if (progress(); as p) {
    @if (viewState() === 'empty') {
      <section class="onb__empty">
        <h2 tabindex="-1" data-focus>No hay pasos disponibles por ahora</h2>
        <p>No pudimos mostrar los pasos de tu registro. Inténtalo de nuevo.</p>
        <button type="button" class="btn" (click)="load()" [disabled]="loading()">Reintentar</button>
      </section>
    } @else {
      <nav aria-label="Pasos del registro">
        <p class="onb__position">
          @if (p.status === 'COMPLETED') {
            Registro completado
          } @else if (position() > 0) {
            Paso {{ position() }} de {{ steps().length }}
          }
        </p>
        <ol class="steps">
          @for (s of steps(); track s.stepId) {
            <li
              class="steps__item"
              [class.steps__item--done]="s.status === 'COMPLETED'"
              [class.steps__item--current]="s.status === 'CURRENT'"
              [attr.aria-current]="s.status === 'CURRENT' ? 'step' : null"
            >
              <span class="steps__marker" aria-hidden="true">{{ s.status === 'COMPLETED' ? '✓' : s.order }}</span>
              <span class="steps__label">{{ s.label }}</span>
              <span class="sr-only">, {{ statusText(s.status) }}</span>
              @if (s.required) {
                <span class="steps__req">Obligatorio</span>
              }
            </li>
          }
        </ol>
      </nav>

      @switch (viewState()) {
        @case ('completed') {
          <section class="onb__done">
            <h2 tabindex="-1" data-focus>¡Tu wallet está activa!</h2>
            <p>Ya puedes empezar a hacer tus pagos de bajo monto.</p>
            <button type="button" class="btn" (click)="goToPayments()">Ir a pagos de bajo monto</button>
          </section>
        }
        @case ('activating') {
          <section class="onb__done">
            <h2 tabindex="-1" data-focus>Estamos activando tu wallet</h2>
            <p>Completaste tu registro. La activación puede tardar unos minutos. Vuelve a abrir la app más tarde.</p>
          </section>
        }
        @case ('closed') {
          <section class="onb__done">
            <h2 tabindex="-1" data-focus>Tu registro se cerró sin completarse</h2>
            <p>Puedes empezar de nuevo cuando quieras.</p>
            <button type="button" class="btn" (click)="load()" [disabled]="loading()">Empezar de nuevo</button>
          </section>
        }
        @case ('ready') {
          @if (currentStep(); as step) {
            <form [formGroup]="stepForm" (ngSubmit)="onContinue()" novalidate [attr.aria-busy]="submitting()">
              <h2 class="onb__step-title" tabindex="-1" data-focus>{{ step.label }}</h2>

              @if (summary().length > 0) {
                <div class="onb__summary" role="alert" tabindex="-1" data-summary-focus>
                  <p class="onb__summary-title">Revisa estos datos para continuar:</p>
                  <ul>
                    @for (item of summary(); track $index) {
                      <li>
                        @if (item.name) {
                          <button type="button" class="link" (click)="focusField(item.name)">{{ item.message }}</button>
                        } @else {
                          {{ item.message }}
                        }
                      </li>
                    }
                  </ul>
                </div>
              }

              @if (currentFields().length === 0) {
                <p class="onb__unavailable">Este paso todavía no está disponible en la aplicación.</p>
              }

              @for (f of currentFields(); track f.name) {
                <div class="field">
                  <label [for]="'f-' + f.name">
                    {{ f.label }}
                    @if (f.required) {
                      <span aria-hidden="true" class="field__star">*</span>
                      <span class="sr-only">(obligatorio)</span>
                    }
                  </label>
                  @if (f.hint) {
                    <p class="field__hint" [id]="'h-' + f.name">{{ f.hint }}</p>
                  }
                  <input
                    class="field__input"
                    [id]="'f-' + f.name"
                    [type]="f.type ?? 'text'"
                    [formControlName]="f.name"
                    [attr.autocomplete]="f.autocomplete ?? null"
                    [attr.aria-required]="f.required ? 'true' : null"
                    [attr.aria-invalid]="errorText(f.name) ? 'true' : null"
                    [attr.aria-describedby]="describedBy(f)"
                  />
                  @if (errorText(f.name); as msg) {
                    <p class="field__error" [id]="'e-' + f.name">{{ msg }}</p>
                  }
                </div>
              }

              <div class="onb__actions">
                <button
                  type="submit"
                  class="btn"
                  [disabled]="submitting() || loading() || currentFields().length === 0"
                >
                  @if (submitting()) {
                    <span class="spinner spinner--small" aria-hidden="true"></span>
                    <span>Enviando…</span>
                  } @else {
                    <span>Continuar</span>
                  }
                </button>
              </div>
              @if (submitting()) {
                <p class="sr-only" role="status">Enviando tus datos…</p>
              }
            </form>
          }
        }
      }
    }
  }
</main>
```

```scss
// onboarding.component.scss
:host {
  display: block;
  color: #1a1a1a;
  background: #fff;
}

.onb {
  max-width: 32rem;
  margin: 0 auto;
  padding: 1rem;
  font-size: 1rem;
  line-height: 1.5;
}

.onb__title { font-size: 1.5rem; margin: 0 0 1rem; }
.onb__step-title { font-size: 1.25rem; margin: 1.5rem 0 1rem; }
.onb__position { font-weight: 600; margin: 0 0 0.5rem; }

.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}

.steps__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid #5f6368;
  border-radius: 0.5rem;
}

.steps__item--current { border: 2px solid #0b57d0; font-weight: 700; }
.steps__item--done { background: #f1f3f4; }

.steps__marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  border: 2px solid #1a1a1a;
  font-weight: 700;
}

.steps__item--current .steps__marker { background: #0b57d0; border-color: #0b57d0; color: #fff; }
.steps__req { margin-left: auto; font-size: 0.875rem; color: #3c4043; }

.field { margin-bottom: 1rem; }
.field label { display: block; font-weight: 600; margin-bottom: 0.25rem; }
.field__star { color: #b00020; margin-left: 0.25rem; }
.field__hint { margin: 0 0 0.25rem; font-size: 0.875rem; color: #3c4043; }

.field__input {
  width: 100%;
  min-height: 2.75rem;
  padding: 0.5rem 0.75rem;
  font-size: 1rem;
  border: 1px solid #5f6368;
  border-radius: 0.5rem;
  box-sizing: border-box;
}

.field__input[aria-invalid='true'] { border: 2px solid #b00020; }
.field__error { color: #b00020; margin: 0.25rem 0 0; font-size: 0.9375rem; }

.onb__error,
.onb__summary {
  border: 2px solid #b00020;
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  margin: 1rem 0;
  color: #1a1a1a;
}

.onb__error-title { margin: 0 0 0.25rem; font-size: 1.125rem; color: #b00020; }
.onb__summary-title { margin: 0 0 0.25rem; font-weight: 700; }
.onb__summary ul { margin: 0; padding-left: 1.25rem; }
.onb__ref { font-size: 0.875rem; color: #3c4043; }
.onb__actions { margin-top: 1rem; }
.onb__unavailable { padding: 0.75rem; border: 1px dashed #5f6368; border-radius: 0.5rem; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 2.75rem;
  min-width: 2.75rem;
  padding: 0.5rem 1.25rem;
  font-size: 1rem;
  font-weight: 600;
  color: #fff;
  background: #0b57d0;
  border: 2px solid #0b57d0;
  border-radius: 0.5rem;
  cursor: pointer;
}

.btn:disabled { background: #5f6368; border-color: #5f6368; cursor: not-allowed; }

.link {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  color: #0b57d0;
  text-decoration: underline;
  cursor: pointer;
  text-align: left;
}

.btn:focus-visible,
.link:focus-visible,
.field__input:focus-visible,
[tabindex='-1']:focus-visible {
  outline: 3px solid #1a1a1a;
  outline-offset: 2px;
}

[tabindex='-1']:focus { outline: 3px solid #1a1a1a; outline-offset: 2px; }

.onb__loading { display: flex; align-items: center; gap: 0.75rem; padding: 1rem 0; }

.spinner {
  width: 1.5rem;
  height: 1.5rem;
  border: 3px solid #c4c7c5;
  border-top-color: #0b57d0;
  border-radius: 50%;
  animation: onb-spin 0.8s linear infinite;
}

.spinner--small { width: 1rem; height: 1rem; border-color: #ffffff80; border-top-color: #fff; }

@keyframes onb-spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .spinner { animation: none; }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
```

```ts
// onboarding-error.mapper.spec.ts
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { toUiError } from './onboarding-error.mapper';

describe('toUiError', () => {
  const http = (status: number, error?: unknown) =>
    new HttpErrorResponse({ status, error, headers: new HttpHeaders() });

  it('traduce estado 0 a sin conexión y reintentable', () => {
    const ui = toUiError(http(0), 'start');
    expect(ui.title).toBe('Sin conexión');
    expect(ui.retryable).toBeTrue();
  });

  it('400 en submit conserva fieldErrors saneados', () => {
    const ui = toUiError(
      http(400, { code: 'X', fieldErrors: [{ field: 'a', message: 'Falta a' }, { bad: 1 }] }),
      'submit',
    );
    expect(ui.fieldErrors).toEqual([{ field: 'a', message: 'Falta a' }]);
  });

  it('400 en start no muestra errores por campo', () => {
    const ui = toUiError(http(400, { fieldErrors: [{ field: 'acquisitionChannel', message: 'x' }] }), 'start');
    expect(ui.fieldErrors).toEqual([]);
  });

  it('401 pide iniciar sesión', () => {
    expect(toUiError(http(401), 'start').requiresLogin).toBeTrue();
  });

  it('403 no es reintentable', () => {
    expect(toUiError(http(403), 'submit').retryable).toBeFalse();
  });

  it('409 requiere actualizar', () => {
    expect(toUiError(http(409), 'submit').refreshRequired).toBeTrue();
  });

  it('5xx usa mensaje genérico sin exponer el cuerpo', () => {
    const ui = toUiError(http(500, { message: 'SQLException at line 3' }), 'start');
    expect(ui.message).not.toContain('SQL');
  });

  it('descarta un correlationId con caracteres no permitidos', () => {
    expect(toUiError(http(500, { correlationId: '<script>' }), 'start').correlationId).toBeUndefined();
    expect(toUiError(http(500, { correlationId: 'abc-123' }), 'start').correlationId).toBe('abc-123');
  });
});
```

```ts
// onboarding.service.spec.ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OnboardingService } from './onboarding.service';
import { OnboardingProgress, UiError } from './onboarding.models';

const progress: OnboardingProgress = {
  sessionId: 's1',
  status: 'IN_PROGRESS',
  totalSteps: 2,
  currentStepIndex: 1,
  currentStepId: 'A',
  steps: [
    { stepId: 'B', order: 2, label: 'B', required: true, status: 'PENDING' },
    { stepId: 'A', order: 1, label: 'A', required: true, status: 'CURRENT' },
  ],
  version: 1,
  walletStatus: 'PENDING',
  nextAction: { type: 'CONTINUE_STEP', target: 'A' },
};

describe('OnboardingService', () => {
  let service: OnboardingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OnboardingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('envía POST /onboarding/sessions con Idempotency-Key y ordena los pasos', () => {
    let result: OnboardingProgress | undefined;
    service.start().subscribe((p) => (result = p));
    const req = httpMock.expectOne('/onboarding/sessions');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    req.flush(progress, { status: 201, statusText: 'Created' });
    expect(result?.steps.map((s) => s.stepId)).toEqual(['A', 'B']);
  });

  it('reutiliza la clave tras un 500 y la renueva tras un éxito', () => {
    service.start().subscribe({ error: () => undefined });
    const r1 = httpMock.expectOne('/onboarding/sessions');
    const key1 = r1.request.headers.get('Idempotency-Key');
    r1.flush({}, { status: 500, statusText: 'Server Error' });

    service.start().subscribe();
    const r2 = httpMock.expectOne('/onboarding/sessions');
    expect(r2.request.headers.get('Idempotency-Key')).toBe(key1);
    r2.flush(progress);

    service.start().subscribe();
    const r3 = httpMock.expectOne('/onboarding/sessions');
    expect(r3.request.headers.get('Idempotency-Key')).not.toBe(key1);
    r3.flush(progress);
  });

  it('traduce 401 a UiError', () => {
    let err: UiError | undefined;
    service.start().subscribe({ error: (e) => (err = e) });
    httpMock.expectOne('/onboarding/sessions').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(err?.requiresLogin).toBeTrue();
  });

  it('submitStep devuelve error controlado mientras el contrato esté pendiente (SP-1)', () => {
    let err: UiError | undefined;
    service.submitStep(progress, {}).subscribe({ error: (e) => (err = e) });
    expect(err?.title).toBe('Paso no disponible');
  });
});
```

```ts
// onboarding.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ONBOARDING_CONFIG, ONBOARDING_STEP_FIELDS } from './onboarding.config';
import { OnboardingComponent } from './onboarding.component';
import { OnboardingService } from './onboarding.service';
import { OnboardingProgress, UiError } from './onboarding.models';

const inProgress: OnboardingProgress = {
  sessionId: 's1',
  status: 'IN_PROGRESS',
  totalSteps: 3,
  currentStepIndex: 2,
  currentStepId: 'IDENTITY_DOCUMENT',
  steps: [
    { stepId: 'PERSONAL_DATA', order: 1, label: 'Datos personales', required: true, status: 'COMPLETED' },
    { stepId: 'IDENTITY_DOCUMENT', order: 2, label: 'Documento', required: true, status: 'CURRENT' },
    { stepId: 'CONFIRMATION', order: 3, label: 'Confirmación', required: true, status: 'PENDING' },
  ],
  version: 2,
  walletStatus: 'PENDING',
  nextAction: { type: 'CONTINUE_STEP', target: 'IDENTITY_DOCUMENT' },
};

const completed: OnboardingProgress = {
  ...inProgress,
  status: 'COMPLETED',
  walletStatus: 'ACTIVE',
  steps: inProgress.steps.map((s) => ({ ...s, status: 'COMPLETED' as const })),
  nextAction: { type: 'GO_TO_LOW_AMOUNT_PAYMENTS', target: 'LOW_AMOUNT_PAYMENTS_ENTRY' },
};

const serverError: UiError = {
  status: 500,
  title: 'Problema temporal',
  message: 'Tuvimos un problema de nuestro lado. Inténtalo de nuevo en unos minutos.',
  fieldErrors: [],
  retryable: true,
  requiresLogin: false,
  refreshRequired: false,
};

describe('OnboardingComponent', () => {
  let fixture: ComponentFixture<OnboardingComponent>;
  let service: jasmine.SpyObj<OnboardingService>;

  function setup(startResult: unknown): void {
    service = jasmine.createSpyObj<OnboardingService>('OnboardingService', ['start', 'submitStep']);
    (service.start as jasmine.Spy).and.returnValue(startResult);
    TestBed.configureTestingModule({
      imports: [OnboardingComponent],
      providers: [
        provideRouter([]),
        { provide: OnboardingService, useValue: service },
        {
          provide: ONBOARDING_CONFIG,
          useValue: { apiBaseUrl: '', routes: { targets: { LOW_AMOUNT_PAYMENTS_ENTRY: '/pagos' } } },
        },
        {
          provide: ONBOARDING_STEP_FIELDS,
          useValue: {
            IDENTITY_DOCUMENT: [{ name: 'documentNumber', label: 'Número de documento', required: true, maxLength: 12 }],
          },
        },
      ],
    });
    fixture = TestBed.createComponent(OnboardingComponent);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  it('muestra "Paso 2 de 3" y marca el paso actual con aria-current', () => {
    setup(of(inProgress));
    expect(el().textContent).toContain('Paso 2 de 3');
    const current = el().querySelector('li[aria-current="step"]');
    expect(current?.textContent).toContain('Documento');
  });

  it('asocia la etiqueta con el campo', () => {
    setup(of(inProgress));
    const input = el().querySelector('#f-documentNumber') as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(el().querySelector('label[for="f-documentNumber"]')).toBeTruthy();
  });

  it('bloquea el avance con un campo obligatorio vacío y muestra un alert', () => {
    setup(of(inProgress));
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(service.submitStep).not.toHaveBeenCalled();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Ingresa número de documento');
  });

  it('muestra un error con role=alert y Reintentar ante un 500', () => {
    setup(throwError(() => serverError));
    const alert = el().querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Problema temporal');
    expect(el().textContent).toContain('Reintentar');
  });

  it('navega a la ruta configurada al completar con wallet activa', () => {
    setup(of(completed));
    const router = TestBed.inject(Router);
    const nav = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const btn = Array.from(el().querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Ir a pagos de bajo monto'),
    ) as HTMLButtonElement;
    btn.click();
    expect(nav).toHaveBeenCalledWith('/pagos');
  });
});
```

## 9) Resumen de limitaciones

- **E1 está completo.** Cubre iniciar o reanudar, el indicador de pasos, los estados de carga, vacío y error, la accesibilidad y la navegación al estado final.
- **Enviar paso y completar quedan bloqueados por SP-1.** `submitStep` devuelve un error controlado y no inventé ningún endpoint.
- **Los formularios de cada paso quedan bloqueados por SP-4.** `ONBOARDING_STEP_FIELDS` está vacío y la UI muestra "paso no disponible" hasta que Cumplimiento y Producto definan los campos.
- **Sin evidencia de ejecución.** Todo es **NO EJECUTADO**: compilación, `ng test`, contraste y lectores de pantalla.