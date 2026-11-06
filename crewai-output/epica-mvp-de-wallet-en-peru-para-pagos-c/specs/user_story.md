# user_story.md

# Épica: MVP de wallet en Perú para pagos cotidianos — Diseño de solución del BFF

> **Estado del documento:** propuesta de arquitectura para validación de Datos, Backend y Frontend.
> **Convención:** los puntos marcados **[HECHO]** salen del Issue. Los marcados **[PROPUESTA]** son decisiones de diseño de este documento. Los marcados **[SUPUESTO]** o **[PENDIENTE]** no están sustentados en el Issue y requieren confirmación.
>
> **Limitación de la verificación previa:** para esta tarea solo recibí el texto del Issue. No tuve acceso al código del repositorio ni al contenido de `packages/bcp-wallet-mock-openapi.yaml`. Por eso:
> - No afirmo qué clases, módulos o tablas existen. Parto de que el repositorio está **sin estructura base**, y esto es un **[SUPUESTO]** hasta que #8 confirme lo contrario.
> - Del mock solo doy por hechos los recursos nombrados en el Issue: `POST /kyc/verifications`, `GET /payments`, `/control/events`, `/callbacks/payments`, `POST /control/reset`, el código `QR_EXPIRED`, y que cubre KYC, wallets, pagos, QR, callbacks, ledger, soporte y conciliación.
> - Las rutas y campos exactos del mock que no figuran en el Issue se marcan **[PENDIENTE-OPENAPI]** y deben verificarse contra el YAML antes de implementar el adaptador (#9).

---

## 0. Historia de usuario

**Como** equipo de producto de la wallet peruana, **quiero** un BFF propio, con arquitectura hexagonal, delante del BCP Wallet Mock, **para que** usuarios y comercios completen onboarding, pagos de bajo monto y cobros con QR con estados claros, sin duplicidad financiera y con trazabilidad operativa. Esto permite medir y mejorar los objetivos del trimestre: caída en checkout de 30% a 8% y onboarding de 8 a 3 minutos.

Historias cubiertas: #2, #3, #4, #5, #6, #7. Fundación técnica cubierta: #8, #9, #10, #11.

---

## 1. Solución propuesta

### 1.1 Qué existe y qué falta

| Elemento | Estado | Fuente |
|---|---|---|
| Mock BCP Wallet v0.3.0 en `https://bcp-wallet-mock.yefri.workers.dev`, sin autenticación, importes en céntimos de PEN | Existe **[HECHO]** | Issue |
| Grupo asignado: `/groups/1` | Existe **[HECHO]** | Issue |
| KYC (`POST /kyc/verifications`, documentos `TEST-*`) | Existe **[HECHO]** | Issue |
| Pagos, QR, wallets, ledger, soporte, conciliación, callbacks y `/control/events` | Existen en el mock; rutas y esquemas exactos **[PENDIENTE-OPENAPI]** | Issue |
| Pasos de onboarding | **No existen** en el mock; los define el BFF | Brechas |
| Estado "expirado" de pago | **No existe**; se deriva (timeout en `PENDING` o `QR_EXPIRED`) | Brechas |
| Fechas en `Payment`, filtros y paginación en `GET /payments` | **No existen**; el BFF guarda `createdAt` y pagina | Brechas |
| Listado por comercio | **No existe**; el mock solo conoce `merchant-demo` y `contact-demo` | Brechas |
| Webhooks push y firmas | **No existen**; se consumen `/control/events` y `/callbacks/payments` | Brechas |
| Corte diario y canal en conciliación | **No existen**; el BFF guarda instantáneas | Brechas |
| Disputas y escritura de acciones de soporte | **No existen** | Brechas |
| BFF, stack, persistencia propia, AGENTS.md, plantilla de PR | **No existen según el Issue** (casillas sin marcar); **[SUPUESTO]** | Issue |

### 1.2 Resumen de la solución

1. **[PROPUESTA]** Un BFF HTTP/JSON (`/api/v1`) con arquitectura hexagonal. El dominio y los casos de uso son independientes del mock. El mock se aísla detrás de un único puerto de salida (`WalletBackendPort`) cuya implementación es el adaptador HTTP del grupo 1 (#9). Esto respeta la restricción de **ecosistema multirriel**: no se asume un único riel.
2. **[PROPUESTA]** El BFF es dueño de lo que el mock no resuelve:
   - progreso de onboarding;
   - `createdAt` y estado derivado de los pagos;
   - registros de idempotencia;
   - eventos analíticos del funnel;
   - instantáneas de conciliación;
   - configuración;
   - cursores de consumo de eventos del mock.
3. **[PROPUESTA]** La idempotencia es **obligatoria** en `POST /payments` mediante el header `Idempotency-Key`. Se garantiza en el BFF con una restricción de unicidad en la base de datos. No se asume que el mock sea idempotente (**[PENDIENTE-OPENAPI]**).
4. **[PROPUESTA]** Los estados asíncronos se exponen con un modelo de estados **de dominio** claro y estable (`PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`, `REVERSED`). Un componente de sincronización consume `/control/events` y `/callbacks/payments` por polling (no hay push) y actualiza el estado.
5. **[PROPUESTA]** Los errores usan un único formato (`code`, `message`, `retryable`, `traceId`). Los fallos de pago se traducen del código del mock a un código de dominio con mensaje apto para el usuario.

### 1.3 Decisiones de diseño y su motivo

| # | Decisión | Motivo |
|---|---|---|
| D1 | El BFF guarda `createdAt` al aceptar el pago, no al recibirlo del mock | El mock no tiene fechas |
| D2 | `EXPIRED` se deriva: `QR_EXPIRED` del mock, o `PENDING` más antiguo que `payment.pending-timeout-seconds` | El mock no tiene estado expirado |
| D3 | Los listados de pagos se sirven desde la persistencia del BFF, no desde `GET /payments` del mock | El mock no filtra ni pagina |
| D4 | Idempotencia con "insertar primero" (`PROCESSING`) y luego llamar al mock | Evita doble cargo ante reintentos concurrentes |
| D5 | Las instantáneas de conciliación se generan a demanda y por un job opcional; el "corte" es el `capturedAt` del BFF | El mock no tiene corte diario |
| D6 | Los reversos solo se exponen si el mock ofrece una operación de reverso. No se simulan disputas | El mock no tiene flujo de disputas (ver §5) |
| D7 | Todos los importes son enteros en céntimos de PEN; la moneda es fija `PEN` | El mock usa céntimos; el alcance es solo Perú |
| D8 | Los usuarios no ven `/control/reset`. Solo lo usa el entorno de pruebas, coordinado | El reset borra datos compartidos del grupo 1 |

---

## 2. Componentes y límites

### 2.1 Estructura hexagonal (**[PROPUESTA]**; nombres finales sujetos a #8)

```
domain/
  model/
    OnboardingProgress, OnboardingStep, KycResult
    Wallet, WalletStatus
    Payment, PaymentStatus, PaymentFailureReason, Money (céntimos, PEN)
    QrCharge, MerchantRef
    IdempotencyRecord
    ReconciliationSnapshot, ReconciliationEntry
    AnalyticsEvent, FunnelStage
    DomainException (+ códigos de error de dominio)
  port/
    input/
      StartOnboardingUseCase
      SubmitIdentityUseCase
      ActivateWalletUseCase
      GetOnboardingUseCase
      CreatePaymentUseCase
      GetPaymentUseCase
      ListPaymentsUseCase
      ReversePaymentUseCase          (condicional, ver D6)
      CreateQrChargeUseCase
      ListMerchantPaymentsUseCase
      TakeReconciliationSnapshotUseCase
      QueryReconciliationUseCase
      RecordAnalyticsEventUseCase
      SyncBackendEventsUseCase       (invocado por scheduler)
      CheckBackendHealthUseCase
    output/
      WalletBackendPort              (KYC, wallet, pagos, QR, eventos, conciliación)
      OnboardingRepositoryPort
      PaymentRecordRepositoryPort
      IdempotencyRepositoryPort
      AnalyticsEventRepositoryPort
      ReconciliationSnapshotRepositoryPort
      EventCursorRepositoryPort
      AppConfigPort
      ClockPort / IdGeneratorPort
application/
  service/
    OnboardingService, PaymentService, QrChargeService,
    MerchantPaymentQueryService, ReconciliationService,
    AnalyticsService, EventSyncService, HealthService
    (solo dependen de domain/model y domain/port)
infrastructure/
  adapter/
    input/
      rest/  OnboardingController, PaymentController, MerchantController,
             ReconciliationController, AnalyticsController, HealthController,
             GlobalExceptionHandler (formato de error)
      scheduler/ EventSyncScheduler (polling de /control/events y /callbacks/payments)
    output/
      bcpmock/   BcpWalletMockClient (implementa WalletBackendPort; base URL del grupo)
      persistence/ repositorios de los puertos *RepositoryPort
  config/
    BcpMockProperties (base-url, timeouts), PaymentProperties (límites, timeout PENDING),
    beans de cableado, propiedades del scheduler
```

Regla de dependencias: `infrastructure → application → domain`. El dominio no importa nada de `infrastructure/`. Los DTOs REST y los DTOs del mock viven **solo** en `infrastructure/adapter/`. Los mapeos mock ↔ dominio se hacen en el adaptador de salida.

### 2.2 Responsabilidades

| Componente | Responsable de | NO responsable de |
|---|---|---|
| **BFF – adapters de entrada** | Validación de formato, mapeo HTTP ↔ casos de uso, formato de error | Reglas de negocio |
| **BFF – application/domain** | Reglas de onboarding, idempotencia, estados de pago derivados, orquestación | Detalles HTTP o de base de datos |
| **BFF – adapter `bcpmock`** | Llamadas al grupo 1, mapeo de DTOs y de errores del mock, timeouts, trazabilidad | Decidir reintentos de negocio. No reintenta `POST` de pago sin clave de idempotencia |
| **BFF – persistencia propia (#11)** | Onboarding, pagos (`createdAt`), idempotencia, analítica, instantáneas, cursores, configuración | Datos maestros del mock (KYC, ledger) |
| **BCP Wallet Mock (grupo 1)** | KYC, wallets, pagos, QR, ledger, soporte, conciliación, eventos | Pasos de onboarding, fechas, paginación, disputas |
| **Frontend** | Presentar pasos y estados, generar `Idempotency-Key` por intento de pago, reutilizarla en reintentos, emitir eventos de funnel | Derivar estados o calcular expiración |
| **Datos** | Esquema físico, índices, restricciones, retención | Reglas de negocio |

### 2.3 Máquina de estados de pago (**[PROPUESTA]**)

```
        ┌──────────► CONFIRMED ──(reverso, si el mock lo soporta)──► REVERSED
PENDING ┼──────────► FAILED
        └──────────► EXPIRED   (derivado: timeout o QR_EXPIRED)
```

- Estados terminales: `CONFIRMED` (salvo reverso), `FAILED`, `EXPIRED`, `REVERSED`.
- El mapeo de los valores reales del mock a estos estados es **[PENDIENTE-OPENAPI]**. Del mock solo se conoce `PENDING` y `QR_EXPIRED`.

---

## 3. Contrato de API del BFF

> Todos los endpoints son **[PROPUESTA]** del BFF (el BFF no existe todavía). No son endpoints del mock.

**Convenciones**
- Base: `/api/v1`. Content-Type: `application/json; charset=utf-8`.
- Importes: entero en céntimos (`amountCents`, `>= 1`). Moneda: `"PEN"` fija.
- Fechas: ISO-8601 UTC (`2026-11-06T15:40:14Z`).
- Cabecera de trazabilidad: el BFF devuelve `X-Trace-Id` en todas las respuestas.
- Autenticación: **[PENDIENTE]** (ver §6). El mock no tiene autenticación. Este contrato no define cómo se autentican usuarios, comercios u operadores.

**Formato de error común**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Texto apto para mostrar o registrar",
  "retryable": false,
  "traceId": "c0a8...",
  "details": [{ "field": "amountCents", "issue": "must be >= 1" }]
}
```
`details` es opcional.

### 3.1 Onboarding (#2)

Pasos definidos por el BFF **[PROPUESTA]**: `IDENTITY_DATA` → `KYC_VERIFICATION` → `WALLET_ACTIVATION` → `COMPLETED`. Estado de la sesión: `IN_PROGRESS`, `KYC_REJECTED`, `COMPLETED`.

#### `POST /onboarding`: iniciar o retomar onboarding
- **Body**

| Campo | Tipo | Validación |
|---|---|---|
| `phoneAlias` | string | requerido; formato **[PENDIENTE]** (alias de pago inmediato; formato a confirmar). Provisional: 9 dígitos, empieza con `9` |

- **201** (nueva) / **200** (ya existía una sesión no completada para el mismo `phoneAlias`):
```json
{ "onboardingId": "ob_01", "status": "IN_PROGRESS", "currentStep": "IDENTITY_DATA",
  "completedSteps": [], "startedAt": "2026-11-06T15:40:14Z" }
```
- **Errores:** 400 `VALIDATION_ERROR`; 409 `ONBOARDING_ALREADY_COMPLETED` (el alias ya tiene wallet activa); 503 `UPSTREAM_UNAVAILABLE`.

#### `PUT /onboarding/{onboardingId}/identity`: enviar datos y ejecutar KYC
- **Path:** `onboardingId` (string, requerido).
- **Body**

| Campo | Tipo | Validación |
|---|---|---|
| `documentType` | enum `DNI` | **[PENDIENTE]** si se aceptan otros tipos |
| `documentNumber` | string | requerido. En el mock debe cumplir `TEST-*`. Fuera del mock: **[PENDIENTE]** |
| `fullName` | string | requerido, 1–120 caracteres |

  Los campos exactos que exige `POST /kyc/verifications` son **[PENDIENTE-OPENAPI]**.
- **200**
```json
{ "onboardingId": "ob_01", "status": "IN_PROGRESS", "currentStep": "WALLET_ACTIVATION",
  "completedSteps": ["IDENTITY_DATA", "KYC_VERIFICATION"], "kyc": { "result": "APPROVED" } }
```
- **Errores:** 400 `VALIDATION_ERROR`; 404 `ONBOARDING_NOT_FOUND`; 409 `ONBOARDING_STEP_INVALID` (paso fuera de orden); 422 `KYC_REJECTED` (la sesión pasa a `KYC_REJECTED`; `retryable=false`); 502 `UPSTREAM_ERROR`; 503 `UPSTREAM_UNAVAILABLE`; 504 `UPSTREAM_TIMEOUT`.

#### `POST /onboarding/{onboardingId}/activate`: activar wallet
- **Body:** vacío.
- **200**
```json
{ "onboardingId": "ob_01", "status": "COMPLETED", "wallet": { "walletId": "w_123", "status": "ACTIVE" },
  "completedAt": "2026-11-06T15:41:02Z" }
```
- La creación de la wallet usa el endpoint de wallets del mock **[PENDIENTE-OPENAPI]**. La operación debe ser **repetible sin crear una segunda wallet**: si la sesión ya está `COMPLETED`, devuelve 200 con la misma wallet.
- **Errores:** 404 `ONBOARDING_NOT_FOUND`; 409 `ONBOARDING_STEP_INVALID` (KYC no aprobado); 502/503/504 de upstream.

#### `GET /onboarding/{onboardingId}`: consultar progreso
- **200:** mismo esquema que `POST /onboarding`, más `wallet` si está completado.
- **Errores:** 404 `ONBOARDING_NOT_FOUND`.

### 3.2 Pagos de bajo monto e idempotencia (#3, #6)

#### `POST /payments`: crear pago
- **Headers:** `Idempotency-Key` **requerido**, string de 16 a 64 caracteres `[A-Za-z0-9_-]` (**[PENDIENTE]** confirmar formato con Frontend, que normalmente genera un UUID).
- **Body**

| Campo | Tipo | Validación |
|---|---|---|
| `walletId` | string | requerido; la wallet debe estar `ACTIVE` |
| `amountCents` | integer | requerido; `>= 1` y `<= payment.max-amount-cents` (**[PENDIENTE]** valor del tope "bajo monto"; los límites del proveedor pueden ser menores que el máximo regulatorio) |
| `currency` | string | opcional; solo `"PEN"` |
| `destination` | objeto | requerido. Uno de: `{ "type": "MERCHANT", "merchantId": "merchant-demo" }`, `{ "type": "CONTACT", "contactId": "contact-demo" }` o `{ "type": "QR", "qrId": "..." }` |
| `description` | string | opcional, máx. 140 caracteres |

  El mock solo conoce `merchant-demo` y `contact-demo`. Otros valores producen `DESTINATION_NOT_FOUND` o el error equivalente del mock.
- **Respuestas**
  - **201** `Payment` recién creado, con estado `CONFIRMED` o `FAILED` si el mock respondió de forma síncrona.
  - **202** `Payment` en estado `PENDING` (el cliente debe consultar `GET /payments/{id}`).
  - **200** repetición con la misma clave y el mismo cuerpo: devuelve el mismo resultado, con el header `Idempotent-Replayed: true`. **No se vuelve a llamar al mock.**
- **Esquema `Payment`**
```json
{ "paymentId": "pay_01", "walletId": "w_123", "amountCents": 1500, "currency": "PEN",
  "destination": { "type": "MERCHANT", "merchantId": "merchant-demo" },
  "status": "PENDING", "failure": null,
  "createdAt": "2026-11-06T15:40:14Z", "updatedAt": "2026-11-06T15:40:14Z",
  "expiresAt": "2026-11-06T15:45:14Z" }
```
  Cuando `status = FAILED`, `failure` es `{ "code": "INSUFFICIENT_FUNDS", "message": "...", "retryable": false }`.
- **Errores**

| HTTP | `code` | Condición | `retryable` |
|---|---|---|---|
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Falta el header o su formato es inválido | false |
| 400 | `VALIDATION_ERROR` | Campo faltante o inválido | false |
| 404 | `WALLET_NOT_FOUND` | `walletId` desconocido | false |
| 404 | `DESTINATION_NOT_FOUND` | Comercio, contacto o QR desconocido | false |
| 409 | `WALLET_NOT_ACTIVE` | Onboarding no completado | false |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Otra petición con la misma clave aún está en curso | true (reintentar con la misma clave) |
| 410 | `QR_EXPIRED` | El QR venció (código tomado del mock) | false |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | false |
| 422 | `AMOUNT_LIMIT_EXCEEDED` | Excede el límite del proveedor o del BFF | false |
| 422 | `INSUFFICIENT_FUNDS` | Saldo insuficiente (**[SUPUESTO]** que el mock lo distingue) | false |
| 502 | `UPSTREAM_ERROR` | Error no mapeable del mock | false |
| 503 | `UPSTREAM_UNAVAILABLE` | Mock caído o no alcanzable | true |
| 504 | `UPSTREAM_TIMEOUT` | Sin respuesta; **el resultado del pago es incierto**: el pago queda `PENDING` y se reconcilia | true (con la misma clave) |

  Solo `QR_EXPIRED` está sustentado por el Issue. Los demás códigos de origen mock son **[SUPUESTO]** hasta revisar el OpenAPI.

**Reglas de idempotencia (#6)** **[PROPUESTA]**
1. Alcance de la clave: `(walletId, Idempotency-Key)`. Es único en base de datos; la restricción la define Datos.
2. `requestHash` = SHA-256 de la representación canónica del body. Misma clave con otro hash → `IDEMPOTENCY_KEY_REUSED`.
3. Flujo: insertar el registro en `PROCESSING` → llamar al mock → guardar `paymentId`, la respuesta y el estado `COMPLETED`.
4. Un duplicado concurrente choca con la restricción de unicidad y recibe `IDEMPOTENCY_IN_PROGRESS` (409). **Decisión pendiente:** esperar unos ms y devolver el resultado, o devolver 409 directamente.
5. Si el BFF se cae entre la llamada al mock y el guardado, el registro queda `PROCESSING`. `EventSyncService` lo resuelve consultando el mock. Esto depende de que el mock permita correlacionar el pago con la clave o con una referencia (**[PENDIENTE-OPENAPI]**). Si no se puede correlacionar, **no se puede garantizar al 100% la ausencia de duplicados** en ese caso límite (ver §5).
6. Retención de claves: **[PENDIENTE]** (propuesta inicial: 24 h).

#### `GET /payments/{paymentId}`: consultar estado
- **200:** `Payment`. El estado `EXPIRED` se calcula al leer si corresponde (D2).
- **Errores:** 404 `PAYMENT_NOT_FOUND`.

#### `GET /payments`: listar pagos de una wallet
- **Query**

| Parámetro | Tipo | Validación |
|---|---|---|
| `walletId` | string | requerido |
| `status` | enum | opcional |
| `from`, `to` | datetime | opcionales, `from <= to`; filtran por `createdAt` |
| `limit` | integer | 1–100, por defecto 20 |
| `cursor` | string | opaco, opcional |

- **200:** `{ "items": [Payment], "nextCursor": "..." | null }`, ordenado por `createdAt` descendente.
- **Errores:** 400 `VALIDATION_ERROR`; 404 `WALLET_NOT_FOUND`.
- Este listado cubre **solo los pagos creados a través del BFF** (los que tienen `createdAt` propio). Los pagos creados directamente en el mock no aparecen.

### 3.3 Fallas y reversos (#7)

#### `POST /payments/{paymentId}/reversal`: **condicional** (D6)
- **Solo se implementa si Backend confirma que el mock ofrece una operación de reverso** (**[PENDIENTE-OPENAPI]**). Si no existe, este endpoint **no se construye** y el criterio de reverso queda cubierto parcialmente (ver §5).
- **Headers:** `Idempotency-Key` requerido. **Body:** `{ "reason": "string, 1-200 caracteres" }`.
- **202** `Payment` (reverso solicitado) o **200** `Payment` con `status = REVERSED`.
- **Errores:** 404 `PAYMENT_NOT_FOUND`; 409 `PAYMENT_NOT_REVERSIBLE` (el pago no está `CONFIRMED`); 409 `REVERSAL_ALREADY_REQUESTED`; 422 `IDEMPOTENCY_KEY_REUSED`; 503/504 de upstream.

**Fallas de pago (sin endpoint propio)**: `Payment.failure` incluye `code`, `message` apto para el usuario y `retryable`. Tabla de traducción **[PROPUESTA]**; códigos de origen **[PENDIENTE-OPENAPI]**:

| Origen | `failure.code` | `retryable` |
|---|---|---|
| `QR_EXPIRED` (mock) | `QR_EXPIRED` (estado `EXPIRED`) | false (pedir QR nuevo) |
| `PENDING` > timeout | `PAYMENT_TIMEOUT` (estado `EXPIRED`) | false (consultar o reintentar con nueva clave solo tras confirmar que no se cobró) |
| Saldo insuficiente | `INSUFFICIENT_FUNDS` | false |
| Límite del proveedor | `AMOUNT_LIMIT_EXCEEDED` | false |
| Cualquier otro | `PAYMENT_FAILED` | false |

### 3.4 Cobro con QR y visibilidad para comercios (#4)

#### `POST /merchants/{merchantId}/qr-charges`: generar cobro QR
- **Headers:** `Idempotency-Key` requerido.
- **Body:** `amountCents` (integer, `>= 1`, `<= payment.max-amount-cents`, requerido), `description` (string opcional, máx. 140), `expiresInSeconds` (integer, 30–900, por defecto `qr.default-ttl-seconds` **[PENDIENTE]**).
- **201:** `{ "qrChargeId": "...", "merchantId": "merchant-demo", "amountCents": 1500, "currency": "PEN", "qrPayload": "...", "status": "ACTIVE", "createdAt": "...", "expiresAt": "..." }`.
- El endpoint del mock que genera el QR y la forma de `qrPayload` son **[PENDIENTE-OPENAPI]**.
- **Errores:** 400 `VALIDATION_ERROR`/`IDEMPOTENCY_KEY_REQUIRED`; 404 `MERCHANT_NOT_FOUND` (solo existe `merchant-demo`); 422 `IDEMPOTENCY_KEY_REUSED`; 503/504 de upstream.

#### `GET /merchants/{merchantId}/payments`: pagos recibidos por el comercio
- **Query:** `status` (opcional), `from`, `to`, `limit` (1–100, por defecto 20), `cursor`.
- **200:** `{ "items": [Payment], "nextCursor": "..." | null }`, con la misma semántica de estados que `GET /payments/{id}`.
- Se sirve desde la persistencia del BFF: el mock no lista por comercio.
- **Errores:** 400 `VALIDATION_ERROR`; 404 `MERCHANT_NOT_FOUND`.
- **Actualización del estado:** `EventSyncService` consulta `/control/events` y `/callbacks/payments` cada `sync.interval-seconds` (**[PENDIENTE]**; propuesta 5 s). Por tanto, el estado visible para el comercio puede tener un retraso de hasta ese intervalo.

### 3.5 Conciliación operativa (#5)

#### `POST /reconciliation/snapshots`: capturar instantánea
- **Body (opcional):** `{ "note": "string, máx. 200" }`.
- **201**
```json
{ "snapshotId": "rs_01", "capturedAt": "2026-11-06T15:50:00Z",
  "totals": { "count": 12, "confirmedCents": 18000, "failedCount": 2, "pendingCount": 1, "expiredCount": 1 },
  "entries": [ { "paymentId": "pay_01", "bffStatus": "CONFIRMED", "backendStatus": "<valor del mock>", "match": true } ],
  "discrepancies": 0 }
```
- Los campos que devuelve la conciliación del mock son **[PENDIENTE-OPENAPI]**. `discrepancies` cuenta las entradas con `match = false` entre el estado del BFF y el del mock/ledger.
- **Errores:** 503/504 de upstream. Si el mock falla, **no se guarda una instantánea incompleta**.

#### `GET /reconciliation/snapshots`
- **Query:** `from`, `to` (datetime sobre `capturedAt`), `limit` (1–100), `cursor`.
- **200:** `{ "items": [ { "snapshotId", "capturedAt", "totals", "discrepancies" } ], "nextCursor": ... }`.
- **Errores:** 400 `VALIDATION_ERROR`.

#### `GET /reconciliation/snapshots/{snapshotId}`
- **200:** instantánea completa. **Errores:** 404 `SNAPSHOT_NOT_FOUND`.

Las instantáneas son **inmutables** una vez creadas. No existe "corte diario" nativo: se simula con instantáneas del BFF (a demanda o por job).

### 3.6 Instrumentación del funnel

#### `POST /analytics/events`
- **Body**

| Campo | Tipo | Validación |
|---|---|---|
| `eventName` | enum | requerido: `ONBOARDING_STARTED`, `ONBOARDING_STEP_COMPLETED`, `ONBOARDING_COMPLETED`, `CHECKOUT_STARTED`, `PAYMENT_SUBMITTED`, `PAYMENT_CONFIRMED`, `PAYMENT_FAILED`, `CHECKOUT_ABANDONED`, `QR_SCANNED` (lista **[PENDIENTE]** con Frontend/Producto) |
| `sessionId` | string | requerido |
| `occurredAt` | datetime | requerido; no más de 24 h en el futuro ni en el pasado |
| `onboardingId`, `paymentId` | string | opcionales |
| `properties` | objeto | opcional, máx. 2 KB, sin datos personales (**[PENDIENTE]** política de PII) |

- **202:** `{ "eventId": "..." }`. **Errores:** 400 `VALIDATION_ERROR`.
- Además, el BFF registra por sí mismo los eventos que ocurren en servidor (`ONBOARDING_COMPLETED`, `PAYMENT_CONFIRMED`, etc.), para no depender solo del cliente.

### 3.7 Verificación de conectividad con el grupo (#9)

#### `GET /internal/health/backend`
- **200:** `{ "status": "UP", "backend": "bcp-wallet-mock", "group": 1, "baseUrl": "https://bcp-wallet-mock.yefri.workers.dev/groups/1", "checkedAt": "..." }`.
- **503** `UPSTREAM_UNAVAILABLE` si el grupo no responde.
- La operación del mock usada para verificar es **[PENDIENTE-OPENAPI]**. Se prefiere una operación de solo lectura, **nunca** `/control/reset`.
- Este endpoint es interno y no debe exponerse al público (**[PENDIENTE]** definición de red).

### 3.8 Contratos no expuestos por HTTP
- `SyncBackendEventsUseCase` (scheduler) consume `/control/events` y `/callbacks/payments` del mock. No es un endpoint del BFF.
- `/control/reset` no se invoca desde el código de producción. Solo se usa en pruebas, **coordinado** entre quienes comparten el grupo 1.

---

## 4. Ejemplos de petición y respuesta

### 4.1 Caso válido: pago de bajo monto a comercio

**Petición**
```http
POST /api/v1/payments
Idempotency-Key: 6f1c2b7e-5a4d-4a0b-9d21-3b7a8c0e1f44
Content-Type: application/json

{ "walletId": "w_123", "amountCents": 1500, "currency": "PEN",
  "destination": { "type": "MERCHANT", "merchantId": "merchant-demo" },
  "description": "Menú del día" }
```

**Recorrido**
1. `PaymentController` (adapter de entrada) valida formato: header presente, `amountCents` entero `>= 1`, moneda `PEN`, destino con tipo válido. Mapea al comando de `CreatePaymentUseCase`.
2. `PaymentService` (application) verifica por `OnboardingRepositoryPort` / `WalletBackendPort` que la wallet existe y está `ACTIVE`, y que el importe no supera el límite configurado (`AppConfigPort`).
3. `IdempotencyRepositoryPort.insertIfAbsent(walletId, key, requestHash)` guarda `PROCESSING`. La inserción tiene éxito porque la clave es nueva.
4. `PaymentService` registra `createdAt` con `ClockPort` y llama a `WalletBackendPort.createPayment(...)`. El adaptador `BcpWalletMockClient` envía la petición al grupo 1 con un timeout configurado.
5. El mock responde que el pago queda pendiente. El adaptador mapea el estado a `PENDING`.
6. `PaymentRecordRepositoryPort.save(...)` guarda el pago con `createdAt`, y la clave pasa a `COMPLETED` con la respuesta guardada. `AnalyticsService` registra `PAYMENT_SUBMITTED`.
7. El controller responde **202** con el `Payment`. Más tarde `EventSyncService` lee los eventos del mock, el pago pasa a `CONFIRMED` y se registra `PAYMENT_CONFIRMED`.

**Respuesta**
```http
HTTP/1.1 202 Accepted
X-Trace-Id: 9b1f...

{ "paymentId": "pay_01", "walletId": "w_123", "amountCents": 1500, "currency": "PEN",
  "destination": { "type": "MERCHANT", "merchantId": "merchant-demo" },
  "status": "PENDING", "failure": null,
  "createdAt": "2026-11-06T15:40:14Z", "updatedAt": "2026-11-06T15:40:14Z",
  "expiresAt": "2026-11-06T15:45:14Z" }
```

**Reintento con la misma clave y el mismo cuerpo** (por ejemplo, tras un corte de red del cliente): el paso 3 encuentra la clave en `COMPLETED` con el mismo `requestHash`. El BFF devuelve la respuesta guardada (**200**, `Idempotent-Replayed: true`) **sin llamar al mock**.

### 4.2 Caso de error: pago con QR vencido

**Petición**
```http
POST /api/v1/payments
Idempotency-Key: a2d9c1f0-77be-4c0e-8f65-0d2b4e9a1c10
Content-Type: application/json

{ "walletId": "w_123", "amountCents": 2500,
  "destination": { "type": "QR", "qrId": "qr_77" } }
```

**Recorrido**
1. El controller valida el formato: correcto.
2. `PaymentService` verifica la wallet `ACTIVE` y el límite: correcto. Se inserta la clave en `PROCESSING`.
3. `BcpWalletMockClient` llama al mock, que responde con el error `QR_EXPIRED` (**[SUPUESTO]** sobre el formato exacto de la respuesta; el código está sustentado por el Issue).
4. El adaptador traduce el error del mock a una excepción de dominio `QrExpiredException`. Ningún tipo del mock sale del adaptador.
5. `PaymentService` guarda el pago como `EXPIRED` con `failure.code = QR_EXPIRED` (así queda trazado en el funnel), marca la clave como `COMPLETED` con ese resultado y registra `PAYMENT_FAILED`. Se decide guardar el resultado para que un reintento con la misma clave devuelva el mismo error, sin otra llamada al mock.
6. `GlobalExceptionHandler` convierte la excepción a HTTP **410** con el formato de error común.

**Respuesta**
```http
HTTP/1.1 410 Gone
X-Trace-Id: 4e7a...

{ "code": "QR_EXPIRED",
  "message": "El código QR ha vencido. Solicita al comercio un nuevo código.",
  "retryable": false,
  "traceId": "4e7a..." }
```

**Variante de error de idempotencia:** misma clave `a2d9...` con `amountCents: 3000` → el hash no coincide → **422** `IDEMPOTENCY_KEY_REUSED`, sin llamar al mock.

---

## 5. Criterios de aceptación verificables

Cada criterio indica cómo se verifica y su estado frente al diseño: **Cumple**, **Cumple parcialmente** o **No se puede cumplir** con el mock actual.

### #2 Registro y activación del MVP de wallet: **Cumple**
- **Dado** un `phoneAlias` válido, **cuando** se llama a `POST /onboarding`, **entonces** responde 201 con `currentStep = IDENTITY_DATA`.
- **Dado** un documento `TEST-*`, **cuando** se envía `PUT /onboarding/{id}/identity`, **entonces** el BFF invoca `POST /kyc/verifications` y avanza a `WALLET_ACTIVATION`.
- **Dado** un KYC rechazado, **entonces** responde 422 `KYC_REJECTED` y la sesión queda en `KYC_REJECTED`.
- **Dado** un KYC aprobado, **cuando** se llama a `POST /onboarding/{id}/activate`, **entonces** la wallet queda `ACTIVE` y la sesión `COMPLETED`. Repetir la llamada no crea otra wallet.
- **Dado** un paso fuera de orden, **entonces** responde 409 `ONBOARDING_STEP_INVALID`.
- **Dado** que se cierra y reabre la aplicación, **cuando** se llama a `GET /onboarding/{id}`, **entonces** se recupera el último paso.
- El objetivo "onboarding de 8 a 3 minutos" se mide con los eventos `ONBOARDING_STARTED` y `ONBOARDING_COMPLETED`. **El diseño habilita la medición; no garantiza el valor.**

### #3 Confirmación de pagos de bajo monto: **Cumple**
- Un pago válido devuelve 201/202 con `status` y `createdAt`.
- Un pago `PENDING` pasa a `CONFIRMED` tras la sincronización de eventos y `GET /payments/{id}` lo refleja.
- Un importe `0`, negativo o mayor al tope → 400 `VALIDATION_ERROR` o 422 `AMOUNT_LIMIT_EXCEEDED`, sin llamar al mock en el primer caso.
- Una wallet no activa → 409 `WALLET_NOT_ACTIVE`.
- El BFF no depende de un único riel: cualquier mapeo específico del mock vive en `adapter/output/bcpmock`.
- La meta "caída en checkout de 30% a 8%" es un resultado de negocio. **El diseño solo la hace medible** (`CHECKOUT_STARTED` vs `PAYMENT_CONFIRMED`).

### #4 Visibilidad del estado de pagos QR para comercios: **Cumple parcialmente**
- `POST /merchants/{id}/qr-charges` devuelve un QR con `expiresAt`.
- `GET /merchants/{id}/payments?status=CONFIRMED` devuelve solo pagos confirmados, paginados.
- Un cobro con QR vencido aparece como `EXPIRED`.
- **Limitaciones:** el mock solo conoce `merchant-demo`; el listado cubre solo pagos creados vía BFF; la actualización es por polling, con retraso de hasta `sync.interval-seconds` (no hay webhooks push).

### #5 Conciliación operativa de transacciones: **Cumple parcialmente**
- `POST /reconciliation/snapshots` guarda una instantánea inmutable con totales y discrepancias.
- `GET /reconciliation/snapshots?from&to` lista instantáneas por rango de `capturedAt`.
- Si el mock falla, no se guarda una instantánea parcial.
- **Limitaciones:** no existe corte diario ni canal en el mock; el corte es el `capturedAt` del BFF. El contenido exacto de la conciliación del mock es **[PENDIENTE-OPENAPI]**.

### #6 Idempotencia en pagos: **Cumple, con una salvedad**
- Sin `Idempotency-Key` → 400 `IDEMPOTENCY_KEY_REQUIRED`.
- Misma clave y mismo cuerpo, N veces → un solo pago en el mock; las respuestas siguientes llevan `Idempotent-Replayed: true`.
- Misma clave y cuerpo distinto → 422 `IDEMPOTENCY_KEY_REUSED`.
- Dos peticiones concurrentes con la misma clave → un solo pago creado y la otra recibe 409 `IDEMPOTENCY_IN_PROGRESS` (o espera, según la decisión pendiente).
- Prueba de concurrencia obligatoria: N hilos con la misma clave; el ledger del mock debe mostrar un único cargo.
- **Salvedad:** si el BFF se cae entre la respuesta del mock y el guardado, la ausencia de duplicados depende de poder correlacionar el pago en el mock. Mientras no se verifique, **no se puede garantizar al 100%** (ver §6, P3).

### #7 Manejo de fallas de pago y reversos: **Cumple parcialmente / No cumple en disputas**
- Cada fallo devuelve un `failure.code` de dominio, un mensaje apto para el usuario y `retryable`.
- `PENDING` mayor al timeout → `EXPIRED` derivado, con `PAYMENT_TIMEOUT`.
- Un timeout del mock (504) deja el pago `PENDING` y reconciliable, nunca se marca `FAILED` sin certeza.
- El reverso funciona **solo si** el mock expone la operación (D6).
- **No se puede cumplir** con este diseño: el flujo de **disputas** y la **escritura de acciones de soporte**, porque el mock no los ofrece. El manejo operativo/legal explícito que pide el riesgo del Issue requiere una decisión de negocio y legal fuera de este diseño (ver §6, P6).

### #8 Definir stack técnico y estructura base: **Cumple parcialmente (pendiente de decisión)**
- La estructura de paquetes de §2.1 existe, con las tres capas y sin dependencias hacia afuera. Esto se verifica con una prueba de arquitectura (por ejemplo ArchUnit si el stack es JVM) que falle si `domain` o `application` importan de `infrastructure`.
- El stack concreto **no está definido en el Issue**. Este documento no lo fija (ver §6, P1).

### #9 Cliente del mock y verificación del grupo: **Cumple (sujeto a OpenAPI)**
- `BcpWalletMockClient` implementa `WalletBackendPort` y usa la base `https://bcp-wallet-mock.yefri.workers.dev/groups/1`, configurable por propiedades (`bcp.mock.base-url`).
- Todas las llamadas apuntan a `/groups/N`, **no a la raíz**.
- `GET /internal/health/backend` devuelve 200 si el grupo 1 responde y 503 si no.
- Se prueba el mapeo de errores del mock a errores de dominio con pruebas de contrato.
- Las pruebas que usan `/control/reset` se ejecutan solo de forma coordinada.

### #10 AGENTS.md y plantilla de PR: **Cumple (documentación)**
- `AGENTS.md` incluye: reglas hexagonales y dirección de dependencias; prohibición de llamar al mock fuera del adaptador; uso del grupo 1; **advertencia sobre `/control/reset` y coordinación**; formato de error común; obligatoriedad de `Idempotency-Key`; importes en céntimos; convención de pruebas.
- La plantilla de PR incluye: issue vinculado, capa afectada, checklist de dependencias hacia adentro, pruebas, impacto en contrato y si se usó `/control/reset`.

### #11 Persistencia propia del BFF: **Cumple (diseño lógico; esquema físico a cargo de Datos)**
- Se almacena lo siguiente (modelo lógico **[PROPUESTA]**, no son tablas definitivas):
  - **onboarding_progress:** `onboardingId`, `phoneAlias`, paso actual, estado, `walletId`, marcas de tiempo.
  - **payment_record:** `paymentId` (del mock), `walletId`, destino, importe, estado de dominio, `failure`, `createdAt`, `updatedAt`.
  - **idempotency_record:** clave, alcance, `requestHash`, estado, respuesta, `createdAt`.
  - **analytics_event:** `eventId`, nombre, sesión, `occurredAt`, propiedades.
  - **reconciliation_snapshot:** instantánea y entradas.
  - **event_cursor:** posición de lectura de `/control/events` y `/callbacks/payments`.
  - **app_config:** límites, timeouts, intervalo de sincronización.
- Se verifica que los datos sobreviven a un reinicio del BFF y que la clave de idempotencia es única por base de datos.

---

## 6. Supuestos y decisiones pendientes para otros roles

### 6.1 Backend

| # | Pendiente | Impacto |
|---|---|---|
| P1 | **Stack técnico (#8).** El Issue no lo fija. Las capas del prompt mencionan repos JPA, lo que sugiere JVM, pero **no es una decisión confirmada** | Bloquea la estructura base |
| P2 | **Contrato exacto del mock**: rutas, esquemas y códigos de error de wallets, pagos, QR, eventos y conciliación (**[PENDIENTE-OPENAPI]**). Se ha asumido que existen operaciones para crear wallet, crear pago y generar QR | Puede cambiar los DTOs del adaptador |
| P3 | **¿El mock es idempotente o permite correlacionar un pago con una referencia del cliente?** Sin esto, no se puede recuperar el estado cuando el BFF se cae entre la llamada y el guardado | Riesgo de duplicidad financiera |
| P4 | **¿Existe operación de reverso en el mock?** Define si se construye `POST /payments/{id}/reversal` | Alcance de #7 |
| P5 | **Valores del enum de estado de pago del mock** y su mapeo a `PENDING/CONFIRMED/FAILED/EXPIRED/REVERSED` | Mapeo del adaptador |
| P6 | **Parámetros**: `payment.max-amount-cents` (qué es "bajo monto"), `payment.pending-timeout-seconds`, `qr.default-ttl-seconds`, `sync.interval-seconds`, timeouts HTTP hacia el mock | Valores de configuración |
| P7 | **Autenticación y autorización del BFF**: el mock no tiene; el BFF debe distinguir usuario, comercio y operador. No se define aquí | Seguridad; falta para exponer `/reconciliation` y `/internal` |
| P8 | **Comportamiento de `IDEMPOTENCY_IN_PROGRESS`:** responder 409 de inmediato o esperar con un límite de tiempo | Experiencia de cliente |
| P9 | Formato del `phoneAlias` y campos que exige `POST /kyc/verifications` | Validaciones de onboarding |

### 6.2 Datos

| # | Pendiente |
|---|---|
| D-1 | Motor de base de datos y esquema físico de las entidades del §5 (#11). El modelo lógico es una propuesta |
| D-2 | **Restricciones de unicidad:** `(walletId, idempotencyKey)` en idempotencia; `paymentId` en pagos; unicidad de `phoneAlias` con onboarding no completado (para que `POST /onboarding` reanude la sesión); unicidad de wallet por `phoneAlias` |
| D-3 | **Concurrencia:** nivel de aislamiento y mecanismo (insertar-primero con restricción única, o bloqueo) para idempotencia y para actualizar el estado del pago desde el scheduler y desde peticiones del usuario a la vez. Regla propuesta: los estados terminales nunca retroceden |
| D-4 | Retención de claves de idempotencia, eventos analíticos y instantáneas; política de PII en `properties` |
| D-5 | Índices para `GET /payments` y `GET /merchants/{id}/payments` (`walletId`/`merchantId` + `createdAt`) y paginación por cursor |
| D-6 | Persistencia de la posición de lectura de `/control/events` para no reprocesar ni perder eventos tras reinicios. Los eventos deben procesarse de forma idempotente |

### 6.3 Frontend

| # | Pendiente |
|---|---|
| F-1 | Generar un `Idempotency-Key` (UUID) **por intento de pago** y **reutilizarlo en cualquier reintento** del mismo intento. Una clave nueva solo se usa si el usuario inicia otro pago. Confirmar el formato |
| F-2 | Mostrar el estado de pago a partir del campo `status` del BFF, sin derivar estados. Para `PENDING`, hacer polling a `GET /payments/{id}` (intervalo a acordar) y mostrar `expiresAt` |
| F-3 | Los pasos de onboarding son del BFF: confirmar que `IDENTITY_DATA → KYC_VERIFICATION → WALLET_ACTIVATION` sirve para el flujo de 3 minutos y si se necesita algún paso adicional |
| F-4 | Lista final de `eventName` del funnel y propiedades permitidas |
| F-5 | Textos al usuario para cada `failure.code` y comportamiento ante 503/504: el resultado de un 504 es **incierto**, no mostrar "falló" sino "verificando" |

### 6.4 Producto / Negocio / Legal

| # | Pendiente |
|---|---|
| N-1 | **Disputas y soporte:** el mock no los soporta. El Issue declara el riesgo de reversos y disputas con manejo operativo/legal explícito. Hace falta definir el proceso manual fuera del sistema, o aceptar que el MVP no lo cubre |
| N-2 | Las metas MAU 1.000–2.000 y CAC PEN 10 **no son verificables con este diseño**: dependen de adquisición y de datos de marketing. Solo se instrumenta el funnel de onboarding y pago |
| N-3 | Los límites por proveedor pueden estar por debajo del máximo regulatorio: se necesita el valor aplicable para el MVP |
| N-4 | Disponibilidad 24x7x365: el BFF no define ventanas de mantenimiento. Los SLOs de disponibilidad quedan **[PENDIENTE]** |

### 6.5 Riesgos operativos a coordinar
- `POST /control/reset` borra todos los datos del **grupo 1**, compartido por quien lo use. Debe acordarse un protocolo (aviso previo, horario) antes de usarlo en pruebas con agentes. No debe invocarse desde código de producción ni desde `GET /internal/health/backend`.
- Los datos creados directamente en el mock, fuera del BFF, no tienen `createdAt` ni registro de idempotencia. Quedan fuera de los listados del BFF y pueden aparecer como discrepancias en la conciliación.

---

**Resumen de lo que este documento NO afirma como hecho:** el stack, las rutas y esquemas del mock distintos de los nombrados en el Issue, la existencia de reverso, la idempotencia del mock, la autenticación del BFF, los valores de límites y timeouts, y la existencia de código previo en el repositorio.