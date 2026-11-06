# user_story.md

# Historia: Registro y activación del MVP de wallet

**Fuente:** `packages/user-stories/user-onboarding-mvp-completion.sp.md` · **Épica:** #1 · **Tareas:** #12 (BFF), #13 (UI), #14 (Analítica)

> **Nota de verificación previa.** En esta sesión solo tengo acceso al texto del Issue, no al repositorio. Por eso:
> - **Existente según el Issue:** únicamente el endpoint `POST /kyc/verifications`, nombrado en la tarea #12. Su contrato (body, respuestas, errores) **no está documentado en el Issue**, así que se trata como supuesto pendiente (sección 6).
> - **Todo lo demás es propuesta de diseño**, no código ni endpoints confirmados. Cada endpoint, evento y modelo está marcado como **[PROPUESTO]**.
> - Antes de implementar, Backend debe contrastar esta propuesta con el código real para no duplicar ni contradecir lo ya construido.

---

## 1) Solución propuesta

Un **BFF de onboarding** que orquesta un flujo guiado de pasos sobre `POST /kyc/verifications`. Sus responsabilidades son:

1. **Gestionar el progreso.** Mantiene una sesión de onboarding con la lista de pasos, el paso actual y el estado de cada uno. Esto cubre el criterio 1.
2. **Validar y bloquear el avance.** Valida cada paso antes de aceptarlo. Si hay datos inválidos o faltantes, devuelve errores por campo con un mensaje accionable y no avanza el paso. Esto cubre el criterio 2.
3. **Activar la wallet.** Al completar todos los pasos requeridos, solicita la activación de la wallet y devuelve la indicación de siguiente paso hacia el punto de entrada de pagos de bajo monto. Esto cubre el criterio 3.
4. **Emitir eventos analíticos.** Emite eventos de inicio, de fin de cada paso y de cierre del flujo. Esto cubre el criterio 4.
5. **Exponer un reporte mensual.** Entrega conteos de nuevos usuarios activos, gasto de adquisición atribuido, CAC y mediana de duración de onboarding. Esto habilita medir los criterios 5 y 6.

**Decisiones de diseño clave:**
- **El servidor es la fuente de verdad del progreso.** La UI no calcula el paso actual; solo lo renderiza a partir de la respuesta. Así el paso actual es consistente aunque el usuario cierre y reabra la app.
- **El catálogo de pasos es configuración, no código de dominio.** Los requisitos de KYC están pendientes con Cumplimiento y Producto, por lo que la lista de pasos debe poder cambiar sin reescribir los casos de uso.
- **Los timestamps los asigna el servidor** (UTC, ISO-8601). Los eventos no dependen del reloj del dispositivo, lo que da una frontera de duración confiable.
- **Idempotencia en las operaciones que mutan estado** (`Idempotency-Key`), porque en móvil son habituales los reintentos por red inestable.

**Alcance de los criterios 5 y 6.** El diseño **no puede garantizar** por sí mismo la reducción de 8 a 3 minutos, MAU de 1,000-2,000 ni CAC de PEN 10. Son resultados de negocio. El diseño solo asegura que sean **medibles y verificables** con datos fiables (ver sección 5).

---

## 2) Componentes y límites

### 2.1 Mapa hexagonal (todos **[PROPUESTOS]**)

Las dependencias van solo hacia adentro: `infrastructure → application → domain`.

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/model/` | `OnboardingSession`, `OnboardingStep`, `StepStatus`, `OnboardingStatus`, `OnboardingEvent`, `OnboardingOutcome` | Reglas puras: orden de pasos, transiciones válidas, cuándo el flujo está completo. Sin imports de infraestructura. |
| `domain/port/input/` | `StartOnboardingUseCase`, `GetOnboardingProgressUseCase`, `SubmitOnboardingStepUseCase`, `CompleteOnboardingUseCase`, `GetOnboardingReportUseCase` | Casos de uso que invoca el exterior. |
| `domain/port/output/` | `OnboardingSessionRepositoryPort`, `KycVerificationPort`, `WalletActivationPort`, `OnboardingEventPublisherPort`, `OnboardingMetricsQueryPort`, `AcquisitionSpendPort` | Contratos hacia persistencia, KYC, wallet, mensajería/analítica y gasto de adquisición. |
| `application/service/` | `OnboardingService`, `OnboardingReportService` | Implementan los casos de uso. Dependen solo de `domain/port/*`. |
| `infrastructure/adapter/input/` | `OnboardingRestController`, `OnboardingReportRestController`, manejador global de errores | Traducen HTTP ↔ casos de uso y errores de dominio ↔ códigos HTTP. |
| `infrastructure/adapter/output/` | Adaptador HTTP hacia `POST /kyc/verifications`, adaptador de repositorio de sesiones, adaptador de activación de wallet, publicador de eventos, consulta de métricas, lector de gasto de adquisición | Implementan los puertos de salida. |
| `infrastructure/config/` | Catálogo de pasos, timeouts/reintentos del cliente KYC, wiring de beans | Configuración. |

### 2.2 Límites de responsabilidad por rol

- **Backend (#12):**
  - Dominio, casos de uso y adaptadores del BFF.
  - Integración con `POST /kyc/verifications`.
  - Activación de la wallet.
  - Emisión de eventos.
- **Frontend (#13):**
  - Renderiza el indicador de pasos y el paso actual con los datos de la respuesta.
  - Muestra los mensajes de error por campo y bloquea el botón "Continuar" mientras no haya paso válido.
  - Navega al punto de entrada de pagos según `nextAction`.
  - **No** decide reglas de negocio ni el orden de los pasos.
- **Datos (#14):**
  - Consume los eventos, calcula la mediana de duración, los nuevos usuarios activos mensuales y el CAC.
  - Define la fuente del gasto de adquisición.
  - Valida las definiciones de métricas con Producto.

### 2.3 Qué falta (brecha identificada)

| Elemento | Estado |
|---|---|
| `POST /kyc/verifications` | Existe según el Issue; contrato no verificado |
| Sesión/progreso de onboarding | No hay evidencia de que exista → propuesto |
| Activación de wallet desde el onboarding | No hay evidencia de qué servicio es dueño del estado de la wallet → supuesto |
| Eventos analíticos de onboarding | No hay evidencia → propuesto |
| Fuente de gasto de adquisición | No definida |
| Punto de entrada de pagos de bajo monto | Existencia y ruta no verificadas |

---

## 3) Contrato de API **[PROPUESTO]**

### 3.1 Convenciones generales

- **Autenticación:** se asume que el usuario se identifica por token en `Authorization: Bearer <token>`. El `userId` se toma del token, **nunca del body**. *Pendiente de confirmar el mecanismo real.*
- **Formato:** JSON, UTF-8. Timestamps ISO-8601 en UTC. Identificadores UUID.
- **Headers:**
  - `Idempotency-Key` (UUID): obligatorio en `POST` y `PUT` que mutan estado.
  - `X-Correlation-Id`: opcional; si falta, el servidor lo genera.
- **Catálogo provisional de pasos.** *Es un supuesto pendiente de Cumplimiento y Producto*, no un requisito confirmado:

| order | stepId | required |
|---|---|---|
| 1 | `PERSONAL_DATA` | sí |
| 2 | `IDENTITY_DOCUMENT` | sí (dispara `POST /kyc/verifications`) |
| 3 | `CONFIRMATION` | sí |

### 3.2 Modelo de progreso (respuesta común)

```json
{
  "sessionId": "uuid",
  "status": "IN_PROGRESS | COMPLETED | CLOSED_INCOMPLETE",
  "totalSteps": 3,
  "currentStepIndex": 2,
  "currentStepId": "IDENTITY_DOCUMENT",
  "steps": [
    { "stepId": "PERSONAL_DATA", "order": 1, "label": "string", "required": true, "status": "COMPLETED | CURRENT | PENDING" }
  ],
  "version": 3,
  "walletStatus": "PENDING | ACTIVE",
  "nextAction": { "type": "CONTINUE_STEP | GO_TO_LOW_AMOUNT_PAYMENTS", "target": "string" }
}
```

- `version` es un contador para control de concurrencia optimista.
- Con `status=COMPLETED`, `nextAction.type = GO_TO_LOW_AMOUNT_PAYMENTS` y `target` es una **clave lógica** (`LOW_AMOUNT_PAYMENTS_ENTRY`). La ruta real la resuelve el frontend. *Pendiente de confirmar.*

### 3.3 Endpoints

#### E1. Iniciar o reanudar onboarding
`POST /onboarding/sessions`

- **Headers:** `Idempotency-Key` (obligatorio).
- **Body:** vacío o `{ "acquisitionChannel": "string (opcional, máx. 50)" }`.
- **Comportamiento:** si el usuario ya tiene una sesión `IN_PROGRESS`, la devuelve (reanudación) sin emitir un nuevo evento de inicio. Si no la tiene, crea una y emite `onboarding_started`.
- **Respuestas:**
  - `201 Created`: sesión nueva, con el modelo de progreso.
  - `200 OK`: sesión existente reanudada.

#### E2. Consultar progreso
`GET /onboarding/sessions/{sessionId}`

- **Respuesta:** `200 OK` con el modelo de progreso.
- **Uso:** la UI lo invoca al abrir la pantalla para mostrar la secuencia de pasos y el paso actual (criterio 1).

#### E3. Enviar un paso
`PUT /onboarding/sessions/{sessionId}/steps/{stepId}`

- **Headers:** `Idempotency-Key` (obligatorio), `If-Match: <version>` (obligatorio, control de concurrencia).
- **Body:** depende del paso. Las validaciones de formato listadas son **supuestas** (sección 6).

| stepId | Campo | Tipo | Validación supuesta |
|---|---|---|---|
| `PERSONAL_DATA` | `firstName` | string | obligatorio, 1-80, sin solo espacios |
| | `lastName` | string | obligatorio, 1-80 |
| | `phone` | string | obligatorio, E.164 (Perú `+51` + 9 dígitos) |
| | `birthDate` | string (fecha) | obligatorio, ISO `YYYY-MM-DD`, no futura, mayor de edad *(edad mínima pendiente)* |
| `IDENTITY_DOCUMENT` | `documentType` | enum | obligatorio; valores permitidos pendientes (supuesto: `DNI`, `CE`) |
| | `documentNumber` | string | obligatorio; formato según tipo (supuesto: `DNI` = 8 dígitos) |
| | `documentImageRef` | string | *Pendiente: depende de si KYC exige imágenes* |
| `CONFIRMATION` | `acceptTerms` | boolean | obligatorio, debe ser `true` |

- **Comportamiento:**
  1. Verifica que la sesión exista y sea del usuario del token.
  2. Verifica que el paso sea el actual (no se puede saltar ni reenviar uno ya completado).
  3. Valida los datos.
  4. Si el paso es `IDENTITY_DOCUMENT`, invoca `POST /kyc/verifications`.
  5. Persiste el paso como `COMPLETED` y avanza `currentStepId`.
  6. Emite `onboarding_step_completed`.
- **Respuesta exitosa:** `200 OK` con el modelo de progreso actualizado. El `version` incrementa y el siguiente paso pasa a `CURRENT`.

#### E4. Completar onboarding y activar wallet
`POST /onboarding/sessions/{sessionId}/complete`

- **Headers:** `Idempotency-Key` (obligatorio), `If-Match: <version>`.
- **Body:** vacío.
- **Precondición:** todos los pasos `required` están `COMPLETED`.
- **Comportamiento:**
  1. Solicita la activación vía `WalletActivationPort`.
  2. Marca la sesión `COMPLETED`.
  3. Emite `onboarding_closed` con `outcome=COMPLETED`.
- **Respuesta:** `200 OK` con `status=COMPLETED`, `walletStatus=ACTIVE` y `nextAction.type=GO_TO_LOW_AMOUNT_PAYMENTS`.
- **Idempotencia:** repetir la llamada con la misma clave o sobre una sesión ya completada devuelve el mismo resultado `200` y no duplica eventos ni la activación.

#### E5. Cerrar sin completar (abandono explícito)
`POST /onboarding/sessions/{sessionId}/close`

- **Body:** `{ "reason": "USER_EXIT" }`. El enum de razones está pendiente.
- **Respuesta:** `200 OK` con `status=CLOSED_INCOMPLETE`.
- **Efecto:** emite `onboarding_closed` con `outcome=ABANDONED`.
- *Si Producto decide que el abandono solo se detecta por inactividad, este endpoint se elimina y el cierre se hace por un proceso de expiración. Decisión pendiente.*

#### E6. Reporte analítico mensual (#14)
`GET /analytics/onboarding/monthly-report?month=YYYY-MM&country=PE`

- **Parámetros:**
  - `month` (obligatorio, formato `YYYY-MM`, no futuro).
  - `country` (opcional, por defecto `PE`, código ISO-3166 alfa-2).
- **Autorización:** rol interno de analítica. *Pendiente de definir.*
- **Respuesta `200 OK`:**

```json
{
  "month": "2026-12",
  "country": "PE",
  "currency": "PEN",
  "newActiveUsers": 0,
  "acquisitionSpend": 0.00,
  "cac": 0.00,
  "onboardingStarted": 0,
  "onboardingCompleted": 0,
  "medianOnboardingSeconds": 0,
  "dataStatus": "COMPLETE | SPEND_DATA_MISSING"
}
```

- Si no hay gasto cargado para el mes, `acquisitionSpend` y `cac` son `null` y `dataStatus=SPEND_DATA_MISSING`. No se inventa un valor.
- `cac = acquisitionSpend / newActiveUsers`, nulo si `newActiveUsers = 0`.

### 3.4 Eventos analíticos **[PROPUESTOS]**

Campos comunes: `eventId` (UUID), `eventType`, `occurredAt` (UTC, asignado por el servidor), `sessionId`, `userId` (pseudonimizado; política pendiente), `country`, `acquisitionChannel`.

| eventType | Se emite cuando | Campos adicionales |
|---|---|---|
| `onboarding_started` | Se crea la sesión (E1, solo en `201`) | none |
| `onboarding_step_completed` | Un paso pasa a `COMPLETED` (E3) | `stepId`, `stepOrder`, `stepDurationMs` |
| `onboarding_closed` | La sesión termina (E4 o E5, o expiración) | `outcome` (`COMPLETED`/`ABANDONED`/`EXPIRED`), `lastStepId`, `totalDurationMs` |

**Frontera de duración (propuesta, pendiente de confirmar):** inicio = `occurredAt` de `onboarding_started`; fin = `occurredAt` de `onboarding_closed` con `outcome=COMPLETED`. La mediana se calcula solo sobre sesiones completadas. Para las reanudadas, la duración es de reloj de pared (incluye pausas). Queda pendiente decidir si se mide el tiempo activo.

### 3.5 Errores

**Formato único:**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Revisa los datos ingresados.",
  "details": [
    { "field": "documentNumber", "code": "INVALID_FORMAT", "message": "El DNI debe tener 8 dígitos. Revisa el número e inténtalo de nuevo." }
  ],
  "correlationId": "string"
}
```

| HTTP | `code` | Cuándo | Mensaje accionable (ejemplo) |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campo faltante o con formato inválido (`details[]` por campo) | "Revisa los datos ingresados." + mensaje por campo |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Falta el header | "Falta la clave de idempotencia." |
| 401 | `UNAUTHENTICATED` | Token ausente o inválido | "Inicia sesión para continuar." |
| 403 | `SESSION_NOT_OWNED` | La sesión no pertenece al usuario | "No tienes acceso a este proceso." |
| 404 | `SESSION_NOT_FOUND` | `sessionId` inexistente | "No encontramos tu proceso de registro. Inícialo de nuevo." |
| 404 | `STEP_NOT_FOUND` | `stepId` no está en el catálogo | "Este paso no existe." |
| 409 | `STEP_OUT_OF_ORDER` | El paso no es el actual | "Completa primero el paso: {paso actual}." |
| 409 | `STEP_ALREADY_COMPLETED` | Reenvío de un paso ya completado con distinta clave | "Este paso ya fue completado." |
| 409 | `VERSION_CONFLICT` | `If-Match` no coincide con la versión actual | "Tu progreso cambió. Actualiza la pantalla para continuar." |
| 409 | `ONBOARDING_INCOMPLETE` | E4 sin todos los pasos requeridos | "Aún faltan pasos por completar." |
| 409 | `SESSION_CLOSED` | La sesión ya está cerrada (`CLOSED_INCOMPLETE`) | "Este proceso ya fue cerrado. Inicia uno nuevo." |
| 422 | `KYC_REJECTED` | KYC rechaza la verificación | "No pudimos verificar tu identidad. Revisa tus datos o intenta con otro documento." *(códigos de rechazo pendientes)* |
| 422 | `PRECONDITION_REQUIRED_HEADER` | Falta `If-Match` | "Falta la versión del proceso." |
| 502 | `KYC_UNAVAILABLE` | Falla o timeout de `POST /kyc/verifications` | "No pudimos verificar tu identidad ahora. Tus datos están guardados; inténtalo en unos minutos." |
| 502 | `WALLET_ACTIVATION_FAILED` | Falla la activación (E4) | "No pudimos activar tu wallet. Inténtalo de nuevo." |
| 500 | `INTERNAL_ERROR` | Error no controlado | "Ocurrió un error inesperado. Inténtalo de nuevo." |

*Observación:* la separación 422 (rechazo de negocio KYC) vs. 502 (indisponibilidad) es una propuesta. Backend debe confirmarla contra las respuestas reales de KYC.

**Regla de consistencia:** ante un error de validación o de KYC, el paso **no** pasa a `COMPLETED`, no se emite `onboarding_step_completed` y `currentStepId` no cambia. Así se garantiza el bloqueo del avance (criterio 2).

---

## 4) Ejemplo de petición y respuesta

### 4.1 Caso válido: enviar el paso `IDENTITY_DOCUMENT`

**Recorrido:**
1. **Entrada.** `OnboardingRestController` recibe `PUT /onboarding/sessions/7c1e…/steps/IDENTITY_DOCUMENT`. Extrae el `userId` del token, `Idempotency-Key` e `If-Match`. Valida la estructura del body y llama a `SubmitOnboardingStepUseCase`.
2. **Aplicación.** `OnboardingService` consulta `OnboardingSessionRepositoryPort`. Comprueba:
   - que la sesión exista y sea del usuario;
   - que `version` coincida con `If-Match`;
   - que `IDENTITY_DOCUMENT` sea el paso actual.
3. **Dominio.** `OnboardingSession` valida las reglas del paso (tipo de documento y formato del número).
4. **Salida (KYC).** `OnboardingService` llama a `KycVerificationPort`. El adaptador traduce los datos al contrato de `POST /kyc/verifications` (mapeo pendiente) y recibe un resultado aprobado.
5. **Dominio.** La sesión marca el paso como `COMPLETED` y avanza a `CONFIRMATION`.
6. **Persistencia y evento.** Se guarda la sesión con `version = 3` y se publica `onboarding_step_completed` mediante `OnboardingEventPublisherPort`.
7. **Respuesta.** El controlador devuelve `200` con el modelo de progreso.

**Petición:**
```http
PUT /onboarding/sessions/7c1e2a9e-5b0f-4a43-9d3e-0c1f6b7a1111/steps/IDENTITY_DOCUMENT
Authorization: Bearer <token>
Idempotency-Key: 3f1d9c52-8a6e-4c0b-9a55-1b2c3d4e5f60
If-Match: 2
Content-Type: application/json

{ "documentType": "DNI", "documentNumber": "12345678" }
```

**Respuesta `200 OK`:**
```json
{
  "sessionId": "7c1e2a9e-5b0f-4a43-9d3e-0c1f6b7a1111",
  "status": "IN_PROGRESS",
  "totalSteps": 3,
  "currentStepIndex": 3,
  "currentStepId": "CONFIRMATION",
  "steps": [
    { "stepId": "PERSONAL_DATA", "order": 1, "label": "Tus datos", "required": true, "status": "COMPLETED" },
    { "stepId": "IDENTITY_DOCUMENT", "order": 2, "label": "Tu documento", "required": true, "status": "COMPLETED" },
    { "stepId": "CONFIRMATION", "order": 3, "label": "Confirmación", "required": true, "status": "CURRENT" }
  ],
  "version": 3,
  "walletStatus": "PENDING",
  "nextAction": { "type": "CONTINUE_STEP", "target": "CONFIRMATION" }
}
```

### 4.2 Caso de error: número de documento inválido

**Recorrido:**
1. **Entrada.** El controlador recibe la misma petición con `"documentNumber": "123"`.
2. **Aplicación.** `OnboardingService` carga la sesión y comprueba propiedad, versión y que sea el paso actual (todo correcto).
3. **Dominio.** La validación del paso falla y lanza `StepValidationException` con el campo `documentNumber` y su código `INVALID_FORMAT`.
4. **No se llama a KYC**, no se persiste el paso y no se emite `onboarding_step_completed`.
5. **Entrada.** El manejador global de errores traduce la excepción a `400 VALIDATION_ERROR`.
6. **Frontend.** Muestra el mensaje bajo el campo, mantiene el paso actual y bloquea el avance.

**Petición:**
```http
PUT /onboarding/sessions/7c1e2a9e-5b0f-4a43-9d3e-0c1f6b7a1111/steps/IDENTITY_DOCUMENT
Idempotency-Key: 9a8b7c6d-1111-2222-3333-444455556666
If-Match: 2

{ "documentType": "DNI", "documentNumber": "123" }
```

**Respuesta `400 Bad Request`:**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Revisa los datos ingresados.",
  "details": [
    { "field": "documentNumber", "code": "INVALID_FORMAT", "message": "El DNI debe tener 8 dígitos. Revisa el número e inténtalo de nuevo." }
  ],
  "correlationId": "c-20261106-0001"
}
```

---

## 5) Criterios de aceptación verificables

| # | Criterio | Cómo se cumple y se verifica |
|---|---|---|
| 1 | Secuencia y paso actual siempre visibles | **Dado** una sesión activa, **cuando** se llama a E2/E3, **entonces** la respuesta incluye `steps[]` con `status`, `currentStepId`, `currentStepIndex` y `totalSteps`. Exactamente un paso está en `CURRENT` mientras `status=IN_PROGRESS`. UI (#13): el indicador refleja esos valores en cada pantalla, incluso al reabrir la app. |
| 2 | Bloqueo ante datos faltantes o inválidos con mensaje accionable | **Dado** un body con campos faltantes o inválidos, **cuando** se llama a E3, **entonces** responde `400 VALIDATION_ERROR` con `details[]` (campo + mensaje), el paso no cambia a `COMPLETED` y `currentStepId` permanece igual. Mismo bloqueo ante `STEP_OUT_OF_ORDER` e `ONBOARDING_INCOMPLETE`. |
| 3 | Wallet activa y acceso al punto de entrada de pagos | **Dado** todos los pasos requeridos completados, **cuando** se llama a E4, **entonces** `walletStatus=ACTIVE`, `status=COMPLETED` y `nextAction.type=GO_TO_LOW_AMOUNT_PAYMENTS`. Una segunda llamada idéntica devuelve el mismo resultado sin duplicar la activación. *Verificable en su totalidad solo cuando se confirme el servicio dueño del estado de la wallet y la ruta de pagos.* |
| 4 | Eventos de inicio, fin de cada paso y cierre | **Dado** un onboarding completo de N pasos, **entonces** existen exactamente 1 `onboarding_started`, N `onboarding_step_completed` (uno por paso) y 1 `onboarding_closed` para la `sessionId`. Un reintento idempotente o una reanudación **no** duplica eventos. Un envío con error no emite `step_completed`. |
| 5 | Mediana de onboarding de 8 a 3 min (cohorte MVP Perú, trimestre posterior al lanzamiento) | **Es una meta de negocio, no una funcionalidad.** El diseño habilita su medición: `medianOnboardingSeconds` en E6, calculada con la frontera `started` → `closed(COMPLETED)`. Verificación: comparar contra la línea base de 8 min (*origen de esa línea base no documentado en el Issue; pendiente*). Cumplir la meta depende del diseño de la UI y de los tiempos de KYC, no solo de este contrato. |
| 6 | Conteos mensuales de nuevos usuarios activos y atribución de gasto para verificar MAU y CAC | E6 devuelve `newActiveUsers`, `acquisitionSpend` y `cac` por mes y país. Verificación: durante los primeros 3 meses, MAU entre 1,000 y 2,000 y CAC ≈ PEN 10. **Limitaciones:** (a) la fuente del gasto de adquisición no está definida, así que sin ella el reporte devuelve `SPEND_DATA_MISSING`; (b) "nuevos usuarios activos" frente a "MAU" requiere una definición única de "activo" (ver sección 6). |

---

## 6) Supuestos y decisiones pendientes para otros roles

### Para Backend
1. **Contrato real de `POST /kyc/verifications`.** No está en el Issue. Faltan campos requeridos, respuesta, códigos de error, si es síncrono o asíncrono, y si admite idempotencia. El mapeo del adaptador y la separación 422/502 dependen de esto.
2. **Servicio dueño del estado de la wallet.** Falta saber cómo se activa (API, evento o escritura directa) y si la activación es idempotente. Hoy está modelado como `WalletActivationPort`.
3. **Si ya existe algún concepto de sesión o progreso de onboarding** en el código. En ese caso hay que reutilizarlo y no duplicarlo.
4. **Concurrencia.** Se propone `version` + `If-Match`. Pendiente confirmar:
   - si se permite una sola sesión `IN_PROGRESS` por usuario (restricción de unicidad propuesta);
   - qué ocurre si KYC es asíncrono y la respuesta llega después de que el usuario reintenta.
5. **Idempotencia.** Definir el TTL del almacén de `Idempotency-Key` y el comportamiento al reutilizar la clave con un body distinto (propuesta: `409`).
6. **Autenticación.** Confirmar que el `userId` sale del token y cómo se autentica un usuario que aún no completó el onboarding.
7. **Expiración de sesiones inactivas.** Definir el plazo y si emite `onboarding_closed` con `outcome=EXPIRED`.
8. **Idioma de los mensajes.** Se asume español en el servidor. Confirmar con Frontend si prefieren códigos y que la UI resuelva textos (i18n).

### Para Frontend
1. **Ruta o pantalla real del punto de entrada de pagos de bajo monto.** Hoy es solo la clave lógica `LOW_AMOUNT_PAYMENTS_ENTRY`. Confirmar que existe.
2. **Manejo de `VERSION_CONFLICT`.** Propuesta: refrescar con E2 y reanudar. Confirmar la UX.
3. **Reintentos.** Reutilizar el mismo `Idempotency-Key` al reintentar el mismo envío y generar uno nuevo para cada envío distinto.
4. **Momento de emisión de eventos.** Los eventos se emiten **solo desde el servidor**. Confirmar que Frontend no emitirá eventos paralelos que dupliquen conteos. Si se requieren eventos de interacción (por ejemplo, vistas de pantalla), eso queda fuera de este diseño.
5. **Mensajes accionables.** Mostrar `details[].message` por campo y `message` como mensaje general.

### Para Datos
1. **Frontera de duración de onboarding** *(pregunta abierta del Issue)*. Propuesta: `onboarding_started.occurredAt` → `onboarding_closed(COMPLETED).occurredAt`, con tiempo de reloj de pared y mediana solo sobre sesiones completadas. **Requiere confirmación de Producto/Datos.** Pendiente decidir si se excluyen las pausas y las sesiones reanudadas.
2. **Definición de "usuario activo".** El criterio 6 habla de "nuevos usuarios activos" y de MAU. Hay que decidir si "nuevo activo" = wallet activada en el mes (propuesta) y cómo se distingue de MAU en sentido estricto (usuarios con actividad mensual).
3. **Fuente y granularidad del gasto de adquisición.** No hay fuente en el Issue. Definir quién lo carga, en qué moneda (PEN), con qué periodicidad, y cómo se atribuye por canal (`acquisitionChannel` es campo opcional propuesto).
4. **Línea base de 8 minutos.** Origen y método de cálculo (¿mediana de qué población y período?) para comparar contra los 3 minutos.
5. **Almacenamiento y retención de eventos**, esquema final, y política de pseudonimización de `userId` (datos personales). No se proponen tablas físicas.
6. **Cohorte MVP Perú.** Cómo se identifica (`country=PE` es una propuesta) y cuál es la fecha de lanzamiento que define "trimestre posterior".
7. **Autorización del reporte E6** (roles) y si Datos prefiere un entregable por lotes en vez de un endpoint.

### Para Cumplimiento y Producto (bloqueante)
1. **Requisitos de KYC y documentación** *(pregunta abierta del Issue)*. El catálogo de pasos, los tipos de documento, la edad mínima y los formatos de la sección 3.3 son **provisionales**. No deben implementarse como definitivos hasta confirmar.
2. **Política de abandono**: si hay cierre explícito (E5), por inactividad o ambos.
3. **Reintentos permitidos ante `KYC_REJECTED`** y qué pasa tras un rechazo definitivo (el flujo actual no lo cubre; queda como alcance por definir).

### Riesgos
- Si KYC resulta lento o asíncrono, la meta de 3 minutos puede ser inalcanzable aunque la UI sea óptima.
- Sin fuente de gasto de adquisición, el CAC no puede verificarse (criterio 6 parcialmente incumplible hasta resolverlo).