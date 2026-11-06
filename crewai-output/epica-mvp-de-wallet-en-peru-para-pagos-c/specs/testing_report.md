# testing_report.md

**Issue:** Épica «MVP de wallet en Perú para pagos cotidianos» (historias #2–#7 y fundación técnica #8–#11)
**Fecha del informe:** 2026-11-06
**Rol:** Ingeniero de pruebas

> **Estado global: NO EJECUTADO.**
> No dispongo de ninguna salida real de comandos (compilación, pruebas, arranque del BFF, llamadas al mock). Ningún caso de este informe tiene resultado obtenido. Las columnas «Resultado esperado» describen lo que debe observarse, no lo que se observó. Un caso omitido por falta de configuración (`Assumptions`) no cuenta como aprobado.

---

## 1. Resumen del Issue y alcance de validación

El Issue es una épica de un BFF de wallet para Perú, delante del BCP Wallet Mock v0.3.0 (grupo 1: `https://bcp-wallet-mock.yefri.workers.dev/groups/1`). Cubre onboarding (#2), pagos de bajo monto (#3), estado QR para comercios (#4), conciliación (#5), idempotencia (#6), fallas y reversos (#7), y la fundación técnica (#8–#11).

**El Issue no trae criterios de aceptación numerados.** Uso los criterios derivados CA-1…CA-13 que declaró Backend (sección 4). No son oficiales y deben confirmarse con producto. Frontend usa los mismos.

**Lo que existe y se puede validar (alcance entregado):**

| Entregable | Contenido | Estado de verificación |
|---|---|---|
| Backend (Quarkus, paquete `pe.bcp.login`) | `POST /api/v1/payments` con `Idempotency-Key` y `GET /api/v1/payments/{paymentId}`. Códigos 201/200/400/404/409. Formato de error `code, message, retryable, traceId`. Persistencia JDBC con `ON CONFLICT (idempotency_key) DO NOTHING`. | Entregado sin compilar ni ejecutar. |
| Frontend (Angular) | `/pagos/nuevo` y `/pagos/:paymentId`, validaciones, mapeo de errores a mensajes, reglas de la clave de idempotencia. | Entregado sin compilar ni ejecutar. |
| Mock BCP (grupo 1) | Servicio en la nube. | Solo se pueden verificar lecturas (humo). |

**No implementado según Backend** (no hay nada que probar salvo declararlo): onboarding #2, vista de comercio/QR #4, conciliación #5, reversos #7, persistencia analítica #11, adaptador HTTP del mock #9, autenticación 401/403, y job de sincronización de eventos.

---

## 2. Artefactos recibidos y límites

| Artefacto | Recibido | Límite |
|---|---|---|
| Issue | Sí | Sin criterios numerados. |
| Arquitectura | Parcial (truncado) | No vi rutas ni esquemas completos. Propone hexagonal (`domain/application/infrastructure`). |
| Datos (modelo) | **No** | No hay DDL ni nombres de tablas. Solo se conoce la columna `idempotency_key` y que existe unicidad sobre ella (por el `ON CONFLICT`). |
| Backend | Parcial (truncado) | Secciones 5 y 6 (comandos, ventana de idempotencia, supuestos) no llegaron. No vi el código fuente. |
| Frontend | Parcial (truncado) | No vi el código ni la sección 8 de comandos. |
| `bcp-wallet-mock-openapi.yaml` | **No** | No puedo verificar esquemas del mock. |
| Código de las clases | **No** | Por eso las pruebas de backend son **de caja negra por HTTP/JDBC**. No se invocan clases ni constructores. |

---

## 3. Contraste entre lo requerido, lo definido y lo implementado

| # | Hallazgo | Impacto en pruebas |
|---|---|---|
| H1 | El Issue no trae criterios de aceptación. | Trazabilidad contra CA derivados; marcado como supuesto. |
| H2 | Arquitectura propone paquetes hexagonales (`domain/application/infrastructure`). Backend entregó `resource/service/repository/dto/model` en `pe.bcp.login`. | Las pruebas no dependen de paquetes internos. Diferencia por confirmar con Arquitectura. |
| H3 | Backend marca como [SUPUESTO] las rutas y códigos 201/200/400/404/409. | Las pruebas los asumen y lo declaran. Si el contrato final difiere, fallarán por contrato, no por defecto. |
| H4 | Los nombres de campos del cuerpo de `POST /payments` no están confirmados. Solo constan `amountCents` y `currency: "PEN"`. Frontend usa `payeeId` como **supuesto pendiente**. | Los tests parametrizan el campo (`-Dbff.payeeField`) y admiten campos extra (`-Dbff.extraJson`). **Requisito pendiente:** confirmar el cuerpo real. |
| H5 | El nombre del campo del id en la respuesta (`paymentId`) se infiere de la ruta `{paymentId}`. No está confirmado. | Supuesto; si no existe, los tests fallan con mensaje explícito. |
| H6 | El formato del `paymentId` (UUID) no está confirmado. | API-14 usa un id no-UUID. Si el formato real es libre, esperaría 404, no 400. |
| H7 | `WalletBackendPort` no tiene implementación (pendiente #9). | El BFF podría no arrancar o fallar al llamar al mock. La integración BFF→mock **no es verificable** hoy (INT-P1). |
| H8 | El límite máximo de monto lo valida el servidor, pero su valor y su código de error no se publicaron. | API-P1 **no verificable**. |
| H9 | Frontend no conoce el límite ni la lista completa de `code` de error. | Sus mensajes para esos casos caen en el mapeo por HTTP; solo se prueban los códigos conocidos. |
| H10 | `requestHash` se calcula sobre «la solicitud». No consta si incluye el destinatario. | API-04 lo comprueba; puede revelar que el hash omite el destinatario. |
| H11 | Riesgo: si `amountCents` es `Long`, Jackson puede aceptar `12.5` truncando a `12` (coerción por defecto). Eso violaría CA-5. | API-08 incluye `12.5` como caso negativo dirigido. |
| H12 | Backend reconoce una «ventana» en CA-2 (sección 6, truncada). | No puedo verificarla. Se cubre con la prueba de concurrencia API-15. |
| H13 | Backend y Frontend declaran que no recibieron observaciones de revisión. | No hay correcciones propuestas que validar (sección 9). |
| H14 | Frontend asume rutas `/pagos/nuevo` y `/pagos/:paymentId` y las etiquetas de formulario. | Los selectores E2E son supuestos. |

---

## 4. Criterios de aceptación derivados (no oficiales)

| ID | Criterio |
|---|---|
| CA-1 | `POST /payments` exige `Idempotency-Key` (patrón `^[A-Za-z0-9_-]{8,64}$`). |
| CA-2 | Reintento con la misma clave y la misma solicitud no duplica el cargo (devuelve el pago original, 200). |
| CA-3 | Misma clave con solicitud distinta da conflicto 409 `IDEMPOTENCY_KEY_REUSED`. |
| CA-4 | Reintento concurrente mientras se procesa da 409 `IDEMPOTENCY_IN_PROGRESS` con `retryable=true`. |
| CA-5 | Importes enteros en céntimos de PEN, moneda fija `PEN`, con límite de monto. |
| CA-6 | Estados de dominio claros: `PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`, `REVERSED`. |
| CA-7 | El BFF guarda `createdAt`. |
| CA-8 | Consulta de un pago por id; 404 si no existe. |
| CA-9 | Validación de entrada en servidor con 400. |
| CA-10 | Errores sin trazas ni SQL, con formato único. |
| CA-11 | Fallo del mock sin respuesta: el pago queda `PENDING`. |
| CA-12 | `EXPIRED` derivado (`QR_EXPIRED` o timeout de `PENDING`). |
| CA-13 | 401/403 (el contrato no define autenticación). |
| UI-1 | (Frontend) Muestra mensajes comprensibles por `code`/HTTP, no muestra `message` del servidor, conserva la clave en reintentos y genera otra si cambia la solicitud. |

---

## 5. Matriz de trazabilidad

Leyenda de estado: **NO EJECUTADO** = hay código de prueba entregado, sin salida real. **NO EJECUTADO (propuesta)** = caso diseñado sin código. **NO VERIFICABLE** = faltan artefactos.

| Criterio | Caso(s) | Tipo | Resultado esperado (observable) | Estado |
|---|---|---|---|---|
| CA-1 | API-01, API-05, API-06, API-07, CT-01 | Positivo / negativo / borde | Con clave válida: 201. Sin clave o con clave inválida (7 o 65 caracteres, espacio, `.`, `+`, `/`): 400 con formato de error. Claves de 8 y 64 caracteres: 201. | NO EJECUTADO |
| CA-2 | API-02, API-15 | Positivo / integración | Segundo envío idéntico: 200 con el mismo `paymentId`. En 10 envíos concurrentes, exactamente un 201. | NO EJECUTADO |
| CA-3 | API-03, API-04 | Negativo | Misma clave con otro monto u otro destinatario: 409, `code=IDEMPOTENCY_KEY_REUSED`, `retryable=false`. | NO EJECUTADO |
| CA-4 | API-15 | Integración / concurrencia | Respuestas no-201 son 200 o 409 `IDEMPOTENCY_IN_PROGRESS` con `retryable=true`. Un solo `paymentId`. | NO EJECUTADO |
| CA-5 | API-01, API-08, API-09, API-10, DB-02, FE-01, FE-04 | Positivo / negativo / borde | Monto 1 → 201. Monto 0, -1, `12.5` o ausente → 400. Moneda `USD` → 400. Columnas de importe sin tipos decimales/flotantes. UI envía céntimos enteros y `PEN`. | NO EJECUTADO |
| CA-5 (límite máximo) | API-P1 | Borde | 400 con el monto límite+1. | NO VERIFICABLE (H8) |
| CA-6 | API-01, API-12, FE-13 | Positivo | `status` ∈ {PENDING, CONFIRMED, FAILED, EXPIRED, REVERSED}. UI muestra el texto correcto y «Estado no reconocido» para otros. | NO EJECUTADO |
| CA-6 (transición asíncrona) | API-P3 | Integración | El estado cambia tras consumir `/control/events`. | NO VERIFICABLE (no hay sync ni adaptador) |
| CA-7 | DB-P1 | Persistencia | `createdAt` del BFF presente y no nulo. | NO VERIFICABLE (no hay esquema de Datos) |
| CA-8 | API-12, API-13, API-14, FE-12 | Positivo / negativo | GET existente → 200. UUID inexistente → 404. Id inválido → 400. UI muestra los mensajes de 404. | NO EJECUTADO |
| CA-9 | API-05, API-06, API-08, API-10, API-11, FE-02, FE-03, FE-04 | Negativo / borde | 400 con formato de error. Cuerpo vacío o JSON malformado → 400. UI valida antes de enviar. | NO EJECUTADO |
| CA-10 | API-05…API-16 (helper `assertErrorFormat`) | Negativo / seguridad | Todo error trae `code`, `message`, `retryable` (booleano), `traceId`. Ninguna respuesta contiene `SQLException`, `org.postgresql`, `java.lang.`, `at pe.bcp`, `SELECT `, `INSERT INTO`, `ON CONFLICT`. | NO EJECUTADO |
| CA-11 | API-P2 | Integración | Con el mock caído, el POST responde y el pago queda `PENDING`. | NO VERIFICABLE (sin adaptador ni doble de prueba; H7) |
| CA-12 | API-P3 | Integración | `QR_EXPIRED` o timeout → `EXPIRED`. | NO VERIFICABLE |
| CA-13 | SEC-P1 | Seguridad | 401/403 en rutas protegidas. | NO VERIFICABLE (sin autenticación definida) |
| Unicidad en persistencia | DB-01, API-15 | Persistencia | Existe índice único exacto sobre `(idempotency_key)`. | NO EJECUTADO |
| UI-1 | FE-01…FE-13 | E2E con red simulada | Ver sección 6.4. | NO EJECUTADO |
| #9 verificación del grupo en la nube | MOCK-01, MOCK-02, MOCK-03 | Integración (humo, solo lectura) | Respuestas 2xx con cuerpo no vacío en la raíz, en `/groups/1/payments` y en `/groups/1/control/events`. | NO EJECUTADO |
| Integración BFF→mock grupo 1 | INT-P1 | Integración | Un pago creado vía BFF aparece una sola vez en `GET /groups/1/payments`. | NO VERIFICABLE (sin adaptador) |
| #2, #4, #5, #7, #11 | — | — | Sin implementación; no hay comportamiento que verificar. | NO VERIFICABLE |
| Accesibilidad (UI) | UI-A11Y-M | Manual | Foco, `role="alert"/"status"`, teclado según el frontend_spec. | NO EJECUTADO (propuesta) |

---

## 6. Casos de prueba detallados

### 6.1 API del BFF (caja negra) — `PaymentApiBlackBoxTest`

**Precondición común:** BFF levantado en `${bff.baseUrl}` con la base de datos migrada. Las pruebas usan claves únicas con prefijo `qa-<runId>-`. Destinatario por defecto: `merchant-demo`; alterno: `contact-demo`. Cuerpo base: `{"amountCents":1250,"currency":"PEN","<payeeField>":"merchant-demo"}`.

| ID | Tipo | Pasos | Datos | Resultado esperado |
|---|---|---|---|---|
| API-01 | Positivo | `POST /api/v1/payments` con clave nueva. | `amountCents=1250` | 201. `paymentId` no vacío. `status` en el conjunto de dominio. No existen `backendPaymentId` ni `payerWalletId`. |
| API-02 | Positivo | Repetir el POST con la misma clave y el mismo cuerpo. | Igual que API-01 | 200. Mismo `paymentId` y mismo `status`. |
| API-03 | Negativo | POST, y luego mismo header con `amountCents=1251`. | Clave fija | 409 `IDEMPOTENCY_KEY_REUSED`, `retryable=false`, `traceId` presente. |
| API-04 | Negativo | POST con `merchant-demo`, y luego misma clave con `contact-demo`. | Clave fija | 409 `IDEMPOTENCY_KEY_REUSED` (depende de que el hash incluya el destinatario; H10). |
| API-05 | Negativo | POST sin header `Idempotency-Key`. | Cuerpo válido | 400 con formato de error. |
| API-06 | Negativo / borde | POST con claves inválidas. | 7 caracteres; 65 caracteres; con espacio; con `.`; con `+`; con `/` | 400 con formato de error. |
| API-07 | Borde | POST con claves de 8 y de 64 caracteres. | Claves aleatorias | 201 en ambos casos. |
| API-08 | Negativo / borde | POST con monto inválido. | `0`, `-1`, `12.5`, ausente | 400 con formato de error. |
| API-09 | Borde | POST con monto mínimo. | `amountCents=1` | 201. |
| API-10 | Negativo | POST con moneda distinta. | `currency="USD"` | 400 con formato de error. |
| API-11 | Negativo | POST con JSON malformado y con cuerpo vacío. | `{` y `` | 400. |
| API-12 | Positivo | POST y luego `GET /api/v1/payments/{paymentId}`. | — | 200. Mismo `paymentId`. `status` de dominio. |
| API-13 | Negativo | GET de un UUID aleatorio. | UUID v4 | 404 con formato de error. |
| API-14 | Negativo | GET con id mal formado. | `no-es-un-uuid` | 400 con formato de error (supuesto H6). |
| API-15 | Integración / concurrencia | 10 POST simultáneos con la misma clave y el mismo cuerpo. | Clave fija | Exactamente un 201. Los demás son 200 o 409 `IDEMPOTENCY_IN_PROGRESS` con `retryable=true`. Un único `paymentId`. GET posterior da 200. |
| API-16 | Negativo / seguridad | Clave `x'; DROP TABLE t;--` y destinatario `'; DROP TABLE x;--`. | — | Clave: 400. Destinatario: status < 500. Ninguna respuesta filtra SQL ni trazas. |

### 6.2 Persistencia — `IdempotencyPersistenceTest` y `IdempotencyKeyFormatContractTest`

| ID | Tipo | Precondiciones | Pasos | Resultado esperado |
|---|---|---|---|---|
| DB-01 | Persistencia | PostgreSQL accesible; `-Dbff.db.url` definido. | Consultar `pg_indexes` por un índice `UNIQUE` exacto sobre `(idempotency_key)`. | Al menos una fila. |
| DB-02 | Persistencia | Igual. | Consultar `information_schema.columns` por columnas `%amount%`. | Existe al menos una columna. Ninguna es `real`, `double precision`, `money` ni `numeric` con escala > 0. |
| DB-P1 | Persistencia | Esquema de Datos. | Verificar `createdAt` no nulo y estados válidos. | NO VERIFICABLE: faltan nombres de tablas. |
| CT-01 | Contrato (unitario) | Ninguna. | Comprobar que la clave del cliente (`pay-` + 32 hex = 36 caracteres) y los bordes cumplen el patrón del servidor, y que los casos inválidos no. | Coincidencias según lo esperado. **Limitación:** el patrón está copiado del frontend_spec; no se importa del código. |

### 6.3 Mock en la nube (solo lectura) — `BcpMockGroup1SmokeTest`

Se ejecuta solo con `-Dmock.smoke=true`. **No llama a `/control/reset`** (borra datos compartidos del grupo 1).

| ID | Tipo | Pasos | Resultado esperado |
|---|---|---|---|
| MOCK-01 | Integración | `GET {mock.baseUrl}/` | 2xx, cuerpo no vacío (índice). |
| MOCK-02 | Integración | `GET {mock.baseUrl}/groups/1/payments` | 2xx, cuerpo no vacío. |
| MOCK-03 | Integración | `GET {mock.baseUrl}/groups/1/control/events` | 2xx, cuerpo no vacío. La ruta bajo el grupo es supuesto; un 404 es hallazgo. |

### 6.4 Frontend E2E con red simulada (Playwright) — `e2e/payment-create.spec.ts`

El BFF se sustituye con `page.route`. Los selectores y textos salen del frontend_spec.

| ID | Tipo | Resultado esperado |
|---|---|---|
| FE-01 | Positivo | Envía POST con `amountCents=1250`, `currency="PEN"`, el campo de destinatario y un `Idempotency-Key` con formato `pay-` + 32 hex. Muestra «En proceso». |
| FE-02 | Negativo | Monto 0 → «El monto debe ser mayor que S/ 0.00.» Sin petición. |
| FE-03 | Negativo | Formulario vacío → «Ingresa el monto.» y «Selecciona un destinatario.» Sin petición. |
| FE-04 | Borde | `12.345` → mensaje de decimales, sin petición. `12,50` (coma) → `amountCents=1250`. |
| FE-05 | Integración | 503 y luego «Reintentar»: misma clave y mismo cuerpo. Mensaje de servicio no disponible. |
| FE-06 | Negativo | 409 `IDEMPOTENCY_IN_PROGRESS` → mensaje «Tu pago se está procesando…» dentro de un `role="alert"`. |
| FE-07 | Negativo | 409 `IDEMPOTENCY_KEY_REUSED` → mensaje y acción «Iniciar un pago nuevo». Al enviar de nuevo, la clave es distinta. |
| FE-08 | Borde | Tras un error, cambiar el monto genera una clave nueva. |
| FE-09 | Negativo | Fallo de red → «No pudimos conectarnos…». |
| FE-10 | Seguridad | El `message` del servidor no se muestra; sí se muestra el `traceId`. |
| FE-11 | Borde | Doble clic rápido genera una sola petición. |
| FE-12 | Negativo | Detalle con 404 → «No encontramos este pago. Verifica el identificador.» |
| FE-13 | Positivo / borde | Detalle muestra el texto por estado: En proceso, Confirmado, No completado, Expirado, Revertido, y «Estado no reconocido» para un valor desconocido. |

### 6.5 Casos propuestos sin código (bloqueados)

| ID | Qué verificaría | Qué falta |
|---|---|---|
| API-P1 | Límite máximo de monto: 400 con límite+1 y 201 con el límite. | Valor y código de error publicados por Backend. |
| API-P2 | CA-11: mock sin respuesta → pago `PENDING`, respuesta controlada. | Adaptador #9 o un doble de `WalletBackendPort` (cuya firma no recibí). |
| API-P3 | CA-6/CA-12: transición por `/control/events`, `QR_EXPIRED` y timeout de `PENDING`. | Sincronización de eventos y adaptador. |
| SEC-P1 | CA-13: 401/403. | Definición de autenticación. |
| INT-P1 | Un pago vía BFF aparece una sola vez en `GET /groups/1/payments` del mock; reintento sin duplicado. | Adaptador #9. Coordinar el uso del grupo 1. |
| UI-A11Y-M | Manual: foco tras éxito/error, `aria-invalid`, `aria-describedby`, operación solo con teclado, estado no solo por color. | Frontend ejecutándose. |

---

## 7. Cobertura por tipo

| Tipo | Cobertura | Justificación si falta |
|---|---|---|
| Positivo | API-01, 02, 07, 09, 12; FE-01, 04, 13 | — |
| Negativo | API-03…06, 08, 10, 11, 13, 14, 16; FE-02, 03, 06, 07, 09, 12 | — |
| Borde | API-06, 07, 08, 09; FE-04, 08, 11; CT-01 | El borde del límite máximo está bloqueado (API-P1). |
| Integración | API-15 (concurrencia + BD), FE-05, FE-07, MOCK-01…03 | BFF↔mock no verificable hasta #9 (INT-P1). |
| Persistencia | DB-01, DB-02, API-15 | `createdAt` y estados persistidos, sin esquema (DB-P1). |
| Seguridad | API-16, FE-10 | Autenticación no definida (SEC-P1). |

---

## 8. Revisión de contratos

### 8.1 API (contrato aprobado, parcial y con supuestos)

| Elemento | Contrato | Verificación | Observación |
|---|---|---|---|
| `POST /api/v1/payments` | Header `Idempotency-Key`; 201 / 200 / 400 / 409 | API-01…API-11, API-15, API-16 | Códigos marcados [SUPUESTO] por Backend. |
| `GET /api/v1/payments/{paymentId}` | 200 / 400 / 404 | API-12…API-14 | Formato del id sin confirmar (H6). |
| Error | `{code, message, retryable, traceId}` | `assertErrorFormat` en cada negativo | `IDEMPOTENCY_KEY_REUSED` (409, false) e `IDEMPOTENCY_IN_PROGRESS` (409, true). El resto de códigos no están publicados. |
| Respuesta de pago | Sin `backendPaymentId` ni `payerWalletId` | API-01 | Los demás campos no están confirmados; solo se comparan `paymentId` y `status`. |
| Cuerpo del POST | `amountCents` (entero), `currency="PEN"`, destinatario | API-01, 08, 10 | El nombre del campo de destinatario es supuesto (H4). |
| Contrato Frontend↔BFF | La clave del cliente cumple el patrón del servidor | CT-01, FE-01 | Equivalente en ambos lados solo si el patrón no cambia. |
| Mock | Operaciones bajo `/groups/N` | MOCK-01…03 | OpenAPI no recibido: esquemas no verificables. |

### 8.2 Persistencia

| Elemento | Verificación | Observación |
|---|---|---|
| Unicidad de `idempotency_key` | DB-01 y API-15 | `ON CONFLICT (idempotency_key)` exige un índice único exacto sobre esa columna. |
| Importes enteros | DB-02 | Depende de que las columnas se llamen `*amount*`. |
| Estados `PROCESSING`/`COMPLETED`, `createdAt`, FK pago↔clave | NO VERIFICABLE | El modelo de Datos no fue recibido. |

---

## 9. Correcciones propuestas por etapas anteriores

Ni Backend ni Frontend recibieron observaciones de revisión, y no hay correcciones declaradas por Arquitectura o Datos en lo recibido. Lo único «corregible» son los supuestos abiertos, que este informe vuelve verificables:

| Pendiente | Verificación incluida | Estado |
|---|---|---|
| Código 409 distinto según estado de la clave | API-03, API-04, API-15 | NO EJECUTADO |
| `amountCents` entero (riesgo de coerción de decimales) | API-08 (`12.5`) | NO EJECUTADO |
| Campos internos no expuestos | API-01 | NO EJECUTADO |
| Ventana de idempotencia (Backend §6, truncada) | API-15 la aproxima; no se puede verificar la ventana descrita | NO VERIFICABLE |
| Nombres de campos, límite de monto, lista de `code`, autenticación | Ver H4, H8, H9, SEC-P1 | NO VERIFICABLE |

---

## 10. Preparación del entorno y datos

**Requisitos pendientes** (no asumo que estén instalados):

1. JDK 17 o superior y Maven (o el wrapper `./mvnw`). El gestor de build final depende de #8; los comandos usan Maven por ser el supuesto más común con Quarkus.
2. Dependencias de prueba en el proyecto del BFF:
   - `org.junit.jupiter:junit-jupiter` (incluye `junit-jupiter-params`), o `quarkus-junit5`, en `test`.
   - Jackson (`jackson-databind`), que llega con `quarkus-rest-jackson`.
   - El driver de PostgreSQL, que llega con `quarkus-jdbc-postgresql`.
3. PostgreSQL accesible para el BFF, con el esquema creado por las migraciones. El mecanismo de migración no está en lo recibido.
4. El adaptador #9 o un doble de `WalletBackendPort`, necesario para arrancar el BFF y para las pruebas de integración con el mock. Hoy el BFF puede no arrancar sin implementación del puerto (H7).
5. Node.js, Angular CLI y `@playwright/test` con Chromium, para el E2E del frontend.
6. Acceso a Internet al mock y coordinación con quienes usan el grupo 1.

**Datos de prueba:**

- Claves de idempotencia únicas por ejecución: `qa-<runId>-<tag>-<n>`.
- Destinatarios: `merchant-demo` y `contact-demo` (únicos conocidos por el mock).
- Montos: 1, 1250, 1251, 0, -1, `12.5`.
- Sin documentos KYC (onboarding no implementado).

---

## 11. Comandos reproducibles

> Los comandos son propuestas. Las rutas del proyecto (`./mvnw`, módulo del BFF, `package.json`) deben ajustarse a la estructura real de #8.

**11.1 Arrancar el BFF (supuesto; el comando real depende de #8):**

```bash
# En la raíz del módulo backend, con DB y configuración del mock ya disponibles
./mvnw quarkus:dev
# El BFF debe escuchar en http://localhost:8080 (supuesto)
```

**11.2 Contrato de clave (sin dependencias de runtime):**

```bash
./mvnw test -Dtest=IdempotencyKeyFormatContractTest
```

**11.3 API del BFF (caja negra):**

```bash
./mvnw test -Dtest=PaymentApiBlackBoxTest \
  -Dbff.baseUrl=http://localhost:8080 \
  -Dbff.payeeField=payeeId \
  -Dbff.payeeValue=merchant-demo
# Si el cuerpo requiere campos adicionales:
#   -Dbff.extraJson='{"payerWalletId":"..."}'
```

**11.4 Persistencia:**

```bash
./mvnw test -Dtest=IdempotencyPersistenceTest \
  -Dbff.db.url=jdbc:postgresql://localhost:5432/<db> \
  -Dbff.db.user=<usuario> -Dbff.db.password=<clave>
```

**11.5 Humo del mock (solo lectura; no usa `/control/reset`):**

```bash
./mvnw test -Dtest=BcpMockGroup1SmokeTest -Dmock.smoke=true
# Opcional: -Dmock.baseUrl=https://bcp-wallet-mock.yefri.workers.dev
```

**11.6 Frontend E2E:**

```bash
npm i -D @playwright/test
npx playwright install chromium
npx ng serve --port 4200 &        # o el comando de arranque real del proyecto
FE_BASE_URL=http://localhost:4200 npx playwright test e2e/payment-create.spec.ts --reporter=list
```

**Cómo interpretar la salida:** una prueba «skipped» significa que faltó configuración (`-Dbff.baseUrl`, `-Dbff.db.url`, `-Dmock.smoke`). No es un pase. Los fallos por contrato (H3–H6) deben reportarse como discrepancia, no como defecto automático.

---

## 12. Limpieza

1. Detener el BFF (`Ctrl+C` en `quarkus:dev`) y `ng serve` (`kill %1` o cerrar la terminal).
2. Eliminar los datos de prueba del BFF. Los nombres de tablas no los conozco; plantilla a completar con el esquema real de Datos:
   ```sql
   -- Reemplazar <tabla_idempotencia> y <tabla_pagos> por los nombres reales.
   -- Orden según las claves foráneas reales (no conocidas).
   DELETE FROM <tabla_pagos>        WHERE <columna_clave_idempotencia> LIKE 'qa-%';
   DELETE FROM <tabla_idempotencia> WHERE idempotency_key LIKE 'qa-%';
   ```
3. **No ejecutar `POST /control/reset`** en el grupo 1 como limpieza: borra datos compartidos. Si fuera imprescindible, coordinarlo con el equipo antes.
4. Las pruebas de humo no crean datos en el mock. Si INT-P1 se ejecuta en el futuro, registrar los `paymentId` creados y coordinar su retirada.
5. Borrar `test-results/` y `playwright-report/` del frontend si no se quieren conservar.

---

## 13. Resultados reales

| Grupo | Estado | Evidencia |
|---|---|---|
| CT-01 | NO EJECUTADO | Sin salida |
| API-01…API-16 | NO EJECUTADO | Sin salida |
| DB-01, DB-02 | NO EJECUTADO | Sin salida |
| MOCK-01…03 | NO EJECUTADO | Sin salida |
| FE-01…FE-13 | NO EJECUTADO | Sin salida |
| API-P1…P3, SEC-P1, INT-P1, DB-P1 | NO VERIFICABLE | Faltan artefactos (sección 6.5) |
| UI-A11Y-M | NO EJECUTADO (propuesta) | — |

Tampoco hay evidencia de que el código de pruebas compile. Se escribió sin acceso al código fuente del proyecto.

---

## 14. Limitaciones

- Contratos de Arquitectura, Backend y Frontend recibidos truncados. Sin modelo de Datos, sin OpenAPI del mock y sin código fuente.
- Las pruebas de backend son de caja negra por HTTP y JDBC; no cubren lógica interna (por ejemplo, el cálculo del hash).
- La unicidad se prueba por el índice y por concurrencia, pero no se inspeccionan filas por falta de nombres de tablas.
- No se validó ninguna llamada real del BFF al mock (sin adaptador).
- Los selectores del E2E son supuestos y pueden requerir ajuste.
- No se cubren onboarding, vista de comercio/QR, conciliación, reversos, analítica ni autenticación, porque no están implementados o definidos.

---

## 15. Código de las pruebas

### 15.1 `src/test/java/pe/bcp/login/api/BffClient.java`

```java
// src/test/java/pe/bcp/login/api/BffClient.java
package pe.bcp.login.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Cliente HTTP minimo para pruebas de caja negra del BFF. No depende de clases de produccion. */
final class BffClient {

    static final String BASE = System.getProperty("bff.baseUrl", "").trim();
    static final String PAYEE_FIELD = System.getProperty("bff.payeeField", "payeeId");
    static final String PAYEE_VALUE = System.getProperty("bff.payeeValue", "merchant-demo");
    static final String EXTRA_JSON = System.getProperty("bff.extraJson", "").trim();

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String RUN_ID = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    static final Set<String> DOMAIN_STATES =
            Set.of("PENDING", "CONFIRMED", "FAILED", "EXPIRED", "REVERSED");
    static final List<String> LEAK_MARKERS = List.of(
            "SQLException", "org.postgresql", "java.lang.", "at pe.bcp",
            "SELECT ", "INSERT INTO", "ON CONFLICT", "stackTrace");

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private BffClient() {}

    record Resp(int status, String body, JsonNode json) {}

    static boolean configured() {
        return !BASE.isEmpty();
    }

    /** Clave valida segun ^[A-Za-z0-9_-]{8,64}$ y unica por ejecucion. */
    static String uniqueKey(String tag) {
        return "qa-" + RUN_ID + "-" + tag + "-" + COUNTER.incrementAndGet();
    }

    static String payload(JsonNode amount, String currency, String payee) throws IOException {
        ObjectNode n = MAPPER.createObjectNode();
        if (amount != null) {
            n.set("amountCents", amount);
        }
        if (currency != null) {
            n.put("currency", currency);
        }
        n.put(PAYEE_FIELD, payee);
        if (!EXTRA_JSON.isEmpty()) {
            n.setAll((ObjectNode) MAPPER.readTree(EXTRA_JSON));
        }
        return MAPPER.writeValueAsString(n);
    }

    static String payload(long cents) throws IOException {
        return payload(LongNode.valueOf(cents), "PEN", PAYEE_VALUE);
    }

    static Resp post(String idemKey, String rawBody) throws IOException, InterruptedException {
        return send("POST", "/api/v1/payments", idemKey, rawBody);
    }

    static Resp get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, null);
    }

    static Resp send(String method, String path, String idemKey, String rawBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (idemKey != null) {
            b.header("Idempotency-Key", idemKey);
        }
        if (rawBody != null) {
            b.header("Content-Type", "application/json");
            b.method(method, BodyPublishers.ofString(rawBody));
        } else {
            b.method(method, BodyPublishers.noBody());
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = null;
        String body = r.body() == null ? "" : r.body();
        if (!body.isBlank()) {
            try {
                json = MAPPER.readTree(body);
            } catch (IOException ignored) {
                json = null;
            }
        }
        return new Resp(r.statusCode(), body, json);
    }

    static String paymentId(Resp r) {
        JsonNode n = r.json() == null ? null : r.json().get("paymentId");
        assertTrue(n != null && n.isTextual() && !n.asText().isBlank(),
                "La respuesta no trae 'paymentId' (nombre inferido de la ruta; ver H5). Cuerpo: " + r.body());
        return n.asText();
    }

    static void assertNoLeak(Resp r) {
        for (String m : LEAK_MARKERS) {
            assertFalse(r.body().contains(m), "La respuesta filtra '" + m + "': " + r.body());
        }
    }

    /** Verifica el formato unico de error. expectedCode/expectedRetryable pueden ser null. */
    static void assertErrorFormat(Resp r, int expectedStatus, String expectedCode,
                                  Boolean expectedRetryable) {
        assertEquals(expectedStatus, r.status(), "Status inesperado. Cuerpo: " + r.body());
        JsonNode j = r.json();
        assertTrue(j != null && j.isObject(), "El error no es un objeto JSON: " + r.body());
        assertTrue(j.hasNonNull("code") && j.get("code").isTextual(), "Falta 'code'");
        assertTrue(j.hasNonNull("message") && j.get("message").isTextual(), "Falta 'message'");
        assertTrue(j.hasNonNull("retryable") && j.get("retryable").isBoolean(),
                "Falta 'retryable' booleano");
        assertTrue(j.hasNonNull("traceId") && !j.get("traceId").asText().isBlank(),
                "Falta 'traceId'");
        if (expectedCode != null) {
            assertEquals(expectedCode, j.get("code").asText());
        }
        if (expectedRetryable != null) {
            assertEquals(expectedRetryable, j.get("retryable").asBoolean());
        }
        assertNoLeak(r);
    }
}
```

### 15.2 `src/test/java/pe/bcp/login/api/PaymentApiBlackBoxTest.java`

```java
// src/test/java/pe/bcp/login/api/PaymentApiBlackBoxTest.java
package pe.bcp.login.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static pe.bcp.login.api.BffClient.DOMAIN_STATES;
import static pe.bcp.login.api.BffClient.PAYEE_VALUE;
import static pe.bcp.login.api.BffClient.assertErrorFormat;
import static pe.bcp.login.api.BffClient.assertNoLeak;
import static pe.bcp.login.api.BffClient.get;
import static pe.bcp.login.api.BffClient.paymentId;
import static pe.bcp.login.api.BffClient.payload;
import static pe.bcp.login.api.BffClient.post;
import static pe.bcp.login.api.BffClient.uniqueKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pe.bcp.login.api.BffClient.Resp;

/**
 * Pruebas de caja negra del BFF (POST/GET /api/v1/payments).
 * Requiere -Dbff.baseUrl=http://host:puerto. Sin esa propiedad las pruebas se OMITEN (no son un pase).
 * Rutas, codigos y nombres de campo: ver supuestos H3-H6 del testing_report.md.
 */
class PaymentApiBlackBoxTest {

    @BeforeAll
    static void requiereConfiguracion() {
        assumeTrue(BffClient.configured(),
                "bff.baseUrl no definido: pruebas OMITIDAS (NO EJECUTADO)");
    }

    // API-01
    @Test
    void api01_crearPagoValido_devuelve201_ocultaCamposInternos() throws Exception {
        Resp r = post(uniqueKey("c01"), payload(1250));
        assertEquals(201, r.status(), "Cuerpo: " + r.body());
        paymentId(r);
        String status = r.json().path("status").asText();
        assertTrue(DOMAIN_STATES.contains(status), "Estado fuera del dominio: " + status);
        assertFalse(r.json().has("backendPaymentId"), "Expone backendPaymentId");
        assertFalse(r.json().has("payerWalletId"), "Expone payerWalletId");
        if (r.json().has("amountCents")) {
            assertEquals(1250L, r.json().get("amountCents").asLong());
        }
        if (r.json().has("currency")) {
            assertEquals("PEN", r.json().get("currency").asText());
        }
    }

    // API-02
    @Test
    void api02_replayMismaSolicitud_devuelve200_mismoPago() throws Exception {
        String key = uniqueKey("r02");
        String body = payload(1250);
        Resp first = post(key, body);
        assertEquals(201, first.status(), "Cuerpo: " + first.body());
        Resp second = post(key, body);
        assertEquals(200, second.status(), "Se esperaba 200 en el replay. Cuerpo: " + second.body());
        assertEquals(paymentId(first), paymentId(second));
        assertEquals(first.json().path("status").asText(), second.json().path("status").asText());
    }

    // API-03
    @Test
    void api03_mismaClaveOtroMonto_409KeyReused() throws Exception {
        String key = uniqueKey("m03");
        assertEquals(201, post(key, payload(1250)).status());
        Resp r = post(key, payload(1251));
        assertErrorFormat(r, 409, "IDEMPOTENCY_KEY_REUSED", false);
    }

    // API-04 (depende de que el hash incluya el destinatario; H10)
    @Test
    void api04_mismaClaveOtroDestinatario_409KeyReused() throws Exception {
        String key = uniqueKey("p04");
        assertEquals(201, post(key, payload(LongNode.valueOf(1250), "PEN", "merchant-demo")).status());
        Resp r = post(key, payload(LongNode.valueOf(1250), "PEN", "contact-demo"));
        assertErrorFormat(r, 409, "IDEMPOTENCY_KEY_REUSED", false);
    }

    // API-05
    @Test
    void api05_sinHeaderIdempotencyKey_400() throws Exception {
        Resp r = post(null, payload(1250));
        assertErrorFormat(r, 400, null, null);
    }

    static Stream<String> clavesInvalidas() {
        return Stream.of(
                "abc1234",                 // 7 caracteres
                "a".repeat(65),            // 65 caracteres
                "pay key 12345",           // espacio
                "pay.key.12345",           // punto
                "pay+key+12345",           // mas
                "pay/key/12345");          // barra
    }

    // API-06
    @ParameterizedTest(name = "clave invalida [{index}]")
    @MethodSource("clavesInvalidas")
    void api06_claveInvalida_400(String key) throws Exception {
        Resp r = post(key, payload(1250));
        assertErrorFormat(r, 400, null, null);
    }

    // API-07
    @Test
    void api07_clavesEnLosBordes8y64_201() throws Exception {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String k8 = "q" + hex.substring(0, 7);
        String k64 = "q" + hex.substring(0, 63);
        assertEquals(8, k8.length());
        assertEquals(64, k64.length());
        assertEquals(201, post(k8, payload(1250)).status());
        assertEquals(201, post(k64, payload(1250)).status());
    }

    static Stream<Arguments> montosInvalidos() throws IOException {
        return Stream.of(
                Arguments.of("cero", payload(LongNode.valueOf(0), "PEN", PAYEE_VALUE)),
                Arguments.of("negativo", payload(LongNode.valueOf(-1), "PEN", PAYEE_VALUE)),
                Arguments.of("decimal 12.5", payload(DoubleNode.valueOf(12.5), "PEN", PAYEE_VALUE)),
                Arguments.of("ausente", payload(null, "PEN", PAYEE_VALUE)));
    }

    // API-08
    @ParameterizedTest(name = "monto invalido: {0}")
    @MethodSource("montosInvalidos")
    void api08_montoInvalido_400(String caso, String body) throws Exception {
        Resp r = post(uniqueKey("a08"), body);
        assertErrorFormat(r, 400, null, null);
    }

    // API-09
    @Test
    void api09_montoMinimo1Centimo_201() throws Exception {
        Resp r = post(uniqueKey("b09"), payload(1));
        assertEquals(201, r.status(), "Cuerpo: " + r.body());
    }

    // API-10
    @Test
    void api10_monedaDistintaDePEN_400() throws Exception {
        Resp r = post(uniqueKey("m10"), payload(LongNode.valueOf(1250), "USD", PAYEE_VALUE));
        assertErrorFormat(r, 400, null, null);
    }

    // API-11
    @Test
    void api11_jsonMalformadoYCuerpoVacio_400() throws Exception {
        assertEquals(400, post(uniqueKey("j11"), "{").status());
        assertEquals(400, post(uniqueKey("v11"), "").status());
    }

    // API-12
    @Test
    void api12_consultarPagoExistente_200() throws Exception {
        Resp created = post(uniqueKey("g12"), payload(1250));
        assertEquals(201, created.status(), "Cuerpo: " + created.body());
        String id = paymentId(created);
        Resp got = get("/api/v1/payments/" + id);
        assertEquals(200, got.status(), "Cuerpo: " + got.body());
        assertEquals(id, paymentId(got));
        assertTrue(DOMAIN_STATES.contains(got.json().path("status").asText()));
        assertFalse(got.json().has("backendPaymentId"));
        assertFalse(got.json().has("payerWalletId"));
    }

    // API-13
    @Test
    void api13_consultarPagoInexistente_404() throws Exception {
        Resp r = get("/api/v1/payments/" + UUID.randomUUID());
        assertErrorFormat(r, 404, null, null);
    }

    // API-14 (supone que el id es un UUID; H6)
    @Test
    void api14_idMalFormado_400() throws Exception {
        Resp r = get("/api/v1/payments/no-es-un-uuid");
        assertErrorFormat(r, 400, null, null);
    }

    // API-15
    @Test
    void api15_concurrenciaMismaClave_unSoloCargo() throws Exception {
        final int n = 10;
        final String key = uniqueKey("k15");
        final String body = payload(1250);
        ExecutorService ex = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Resp>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                futures.add(ex.submit(() -> {
                    ready.countDown();
                    go.await();
                    return post(key, body);
                }));
            }
            ready.await();
            go.countDown();
            List<Resp> results = new ArrayList<>();
            for (Future<Resp> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            long created = results.stream().filter(r -> r.status() == 201).count();
            assertEquals(1, created, "Debe haber exactamente un 201");
            Set<String> ids = new HashSet<>();
            for (Resp r : results) {
                if (r.status() == 201 || r.status() == 200) {
                    ids.add(paymentId(r));
                } else {
                    assertErrorFormat(r, 409, "IDEMPOTENCY_IN_PROGRESS", true);
                }
            }
            assertEquals(1, ids.size(), "Mas de un paymentId para la misma clave: " + ids);
            Resp got = get("/api/v1/payments/" + ids.iterator().next());
            assertEquals(200, got.status());
        } finally {
            ex.shutdownNow();
        }
    }

    // API-16
    @Test
    void api16_inyeccionSql_noFiltraInformacion() throws Exception {
        Resp badKey = post("x'; DROP TABLE t;--", payload(1250));
        assertErrorFormat(badKey, 400, null, null);

        String body = payload(LongNode.valueOf(1250), "PEN", "'; DROP TABLE x;--");
        Resp badPayee = post(uniqueKey("s16"), body);
        assertTrue(badPayee.status() < 500, "Error de servidor: " + badPayee.body());
        assertNoLeak(badPayee);
    }
}
```

### 15.3 `src/test/java/pe/bcp/login/api/IdempotencyKeyFormatContractTest.java`

```java
// src/test/java/pe/bcp/login/api/IdempotencyKeyFormatContractTest.java
package pe.bcp.login.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * CT-01. Contrato de formato de clave entre cliente (pay- + 32 hex) y servidor
 * (^[A-Za-z0-9_-]{8,64}$). El patron esta COPIADO de los specs de Backend/Frontend, no importado
 * del codigo de produccion: si el patron real cambia, esta prueba no lo detecta.
 */
class IdempotencyKeyFormatContractTest {

    private static final Pattern SERVER = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Test
    void claveDelCliente_cumpleElPatronDelServidor() {
        for (int i = 0; i < 100; i++) {
            String key = "pay-" + UUID.randomUUID().toString().replace("-", "");
            assertEquals(36, key.length());
            assertTrue(SERVER.matcher(key).matches(), key);
        }
    }

    @Test
    void bordesDelPatron() {
        assertTrue(SERVER.matcher("a".repeat(8)).matches());
        assertTrue(SERVER.matcher("a".repeat(64)).matches());
        assertFalse(SERVER.matcher("a".repeat(7)).matches());
        assertFalse(SERVER.matcher("a".repeat(65)).matches());
        assertFalse(SERVER.matcher("pay key 1234").matches());
        assertFalse(SERVER.matcher("pay.key.1234").matches());
    }
}
```

### 15.4 `src/test/java/pe/bcp/login/api/IdempotencyPersistenceTest.java`

```java
// src/test/java/pe/bcp/login/api/IdempotencyPersistenceTest.java
package pe.bcp.login.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * DB-01 y DB-02. Solo usan nombres conocidos: la columna idempotency_key (por el ON CONFLICT del spec)
 * y columnas '%amount%'. Requiere -Dbff.db.url (+ -Dbff.db.user, -Dbff.db.password). Solo lectura.
 */
class IdempotencyPersistenceTest {

    private static final String URL = System.getProperty("bff.db.url", "").trim();
    private static final String USER = System.getProperty("bff.db.user", "");
    private static final String PASS = System.getProperty("bff.db.password", "");

    @BeforeAll
    static void requiereConfiguracion() {
        assumeTrue(!URL.isEmpty(), "bff.db.url no definido: pruebas OMITIDAS (NO EJECUTADO)");
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // DB-01
    @Test
    void db01_existeIndiceUnicoExactoSobreIdempotencyKey() throws Exception {
        String sql = "SELECT tablename, indexname, indexdef FROM pg_indexes "
                + "WHERE schemaname = current_schema() "
                + "AND indexdef ILIKE 'CREATE UNIQUE INDEX%' "
                + "AND indexdef ~* '\\(idempotency_key\\)'";
        List<String> found = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString("tablename") + "." + rs.getString("indexname"));
            }
        }
        assertFalse(found.isEmpty(),
                "No hay indice unico exacto sobre (idempotency_key): ON CONFLICT no funcionaria");
    }

    // DB-02
    @Test
    void db02_importesNoUsanTiposDecimalesNiFlotantes() throws Exception {
        String sql = "SELECT table_name, column_name, data_type, numeric_scale "
                + "FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND column_name ILIKE '%amount%'";
        int columnas = 0;
        List<String> malas = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                columnas++;
                String tipo = rs.getString("data_type");
                int escala = rs.getInt("numeric_scale");
                boolean mal = tipo.equals("real") || tipo.equals("double precision")
                        || tipo.equals("money") || (tipo.equals("numeric") && escala > 0);
                if (mal) {
                    malas.add(rs.getString("table_name") + "." + rs.getString("column_name")
                            + " (" + tipo + ")");
                }
            }
        }
        assertTrue(columnas > 0, "No se encontro ninguna columna '%amount%' (nombre real pendiente de Datos)");
        assertTrue(malas.isEmpty(), "Importes con tipo no entero: " + malas);
    }
}
```

### 15.5 `src/test/java/pe/bcp/login/api/BcpMockGroup1SmokeTest.java`

```java
// src/test/java/pe/bcp/login/api/BcpMockGroup1SmokeTest.java
package pe.bcp.login.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MOCK-01..03. SOLO LECTURA sobre el grupo 1. No llama a /control/reset (borra datos compartidos).
 * Se activa con -Dmock.smoke=true. La ruta /groups/1/control/events es un supuesto.
 */
class BcpMockGroup1SmokeTest {

    private static final String BASE = System.getProperty("mock.baseUrl",
            "https://bcp-wallet-mock.yefri.workers.dev").replaceAll("/+$", "");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    static void requiereActivacion() {
        assumeTrue(Boolean.getBoolean("mock.smoke"),
                "mock.smoke no es true: pruebas OMITIDAS (NO EJECUTADO)");
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void assert2xxConCuerpo(String path) throws Exception {
        HttpResponse<String> r = get(path);
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 300,
                path + " devolvio " + r.statusCode() + ": " + r.body());
        assertFalse(r.body() == null || r.body().isBlank(), path + " devolvio cuerpo vacio");
    }

    @Test
    void mock01_raizIndice() throws Exception {
        assert2xxConCuerpo("/");
    }

    @Test
    void mock02_grupo1ListaPagos() throws Exception {
        assert2xxConCuerpo("/groups/1/payments");
    }

    @Test
    void mock03_grupo1Eventos() throws Exception {
        assert2xxConCuerpo("/groups/1/control/events");
    }
}
```

### 15.6 `e2e/payment-create.spec.ts`

```ts
// e2e/payment-create.spec.ts
// Frontend E2E con el BFF simulado por page.route. Requiere @playwright/test y `ng serve`.
// Supuestos (H4, H14): rutas /pagos/nuevo y /pagos/:id; etiquetas "Destinatario" y "Monto";
// botones "Pagar", "Reintentar", "Iniciar un pago nuevo"; campo de destinatario = BFF_PAYEE_FIELD.
import { test, expect, Page, Route } from '@playwright/test';

const BASE = process.env.FE_BASE_URL ?? 'http://localhost:4200';
const CREATE_PATH = process.env.FE_CREATE_PATH ?? '/pagos/nuevo';
const DETAIL_PATH = (id: string) => (process.env.FE_DETAIL_PATH ?? '/pagos/:id').replace(':id', id);
const PAYEE_FIELD = process.env.BFF_PAYEE_FIELD ?? 'payeeId';
const PAYMENT_ID = '3f2b8c1e-0000-4000-8000-000000000001';
const SERVER_RE = /^[A-Za-z0-9_-]{8,64}$/;

type Captured = { key: string | undefined; body: any };
type Handler = (route: Route, n: number) => Promise<void>;

const okBody = { paymentId: PAYMENT_ID, status: 'PENDING', amountCents: 1250, currency: 'PEN' };
const errBody = (code: string, retryable: boolean, message = 'MENSAJE-INTERNO-XYZ') => ({
  code, message, retryable, traceId: 'trace-abc-123',
});

async function stubCreate(page: Page, handler: Handler): Promise<Captured[]> {
  const captured: Captured[] = [];
  await page.route('**/api/v1/payments', async (route) => {
    const req = route.request();
    if (req.method() !== 'POST') return route.continue();
    captured.push({ key: req.headers()['idempotency-key'], body: req.postDataJSON() });
    await handler(route, captured.length);
  });
  return captured;
}

async function open(page: Page) {
  await page.goto(`${BASE}${CREATE_PATH}`);
}
async function fillForm(page: Page, amount: string, payee = 'merchant-demo') {
  await page.getByLabel(/destinatario/i).selectOption(payee);
  await page.getByLabel(/monto/i).fill(amount);
}
async function submit(page: Page) {
  await page.getByRole('button', { name: /^pagar$/i }).click();
}

test('FE-01 envio valido: cuerpo, clave y estado visible', async ({ page }) => {
  const cap = await stubCreate(page, (r) => r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByText('En proceso').first()).toBeVisible();
  expect(cap).toHaveLength(1);
  expect(cap[0].body.amountCents).toBe(1250);
  expect(cap[0].body.currency).toBe('PEN');
  expect(cap[0].body[PAYEE_FIELD]).toBe('merchant-demo');
  expect(cap[0].key).toMatch(/^pay-[0-9a-f]{32}$/);
  expect(cap[0].key).toMatch(SERVER_RE);
});

test('FE-02 monto cero: mensaje y sin peticion', async ({ page }) => {
  const cap = await stubCreate(page, (r) => r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '0');
  await submit(page);
  await expect(page.getByText('El monto debe ser mayor que S/ 0.00.')).toBeVisible();
  expect(cap).toHaveLength(0);
});

test('FE-03 formulario vacio: mensajes de requeridos', async ({ page }) => {
  const cap = await stubCreate(page, (r) => r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await submit(page);
  await expect(page.getByText('Ingresa el monto.')).toBeVisible();
  await expect(page.getByText('Selecciona un destinatario.')).toBeVisible();
  expect(cap).toHaveLength(0);
});

test('FE-04 formato de monto: 12.345 invalido, 12,50 aceptado', async ({ page }) => {
  const cap = await stubCreate(page, (r) => r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '12.345');
  await submit(page);
  await expect(page.getByText('Usa solo números con hasta 2 decimales. Ejemplo: 12.50')).toBeVisible();
  expect(cap).toHaveLength(0);
  await page.getByLabel(/monto/i).fill('12,50');
  await submit(page);
  await expect.poll(() => cap.length).toBe(1);
  expect(cap[0].body.amountCents).toBe(1250);
});

test('FE-05 reintento tras 503 conserva clave y cuerpo', async ({ page }) => {
  const cap = await stubCreate(page, (r, n) =>
    n === 1
      ? r.fulfill({ status: 503, json: errBody('BACKEND_DOWN', true) })
      : r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByText(/El servicio no está disponible por ahora/)).toBeVisible();
  await page.getByRole('button', { name: /reintentar/i }).click();
  await expect.poll(() => cap.length).toBe(2);
  expect(cap[1].key).toBe(cap[0].key);
  expect(cap[1].body).toEqual(cap[0].body);
});

test('FE-06 409 IDEMPOTENCY_IN_PROGRESS', async ({ page }) => {
  await stubCreate(page, (r) =>
    r.fulfill({ status: 409, json: errBody('IDEMPOTENCY_IN_PROGRESS', true) }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText(/Tu pago se está procesando/)).toBeVisible();
});

test('FE-07 409 IDEMPOTENCY_KEY_REUSED y pago nuevo con otra clave', async ({ page }) => {
  const cap = await stubCreate(page, (r, n) =>
    n === 1
      ? r.fulfill({ status: 409, json: errBody('IDEMPOTENCY_KEY_REUSED', false) })
      : r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByText(/Esta solicitud ya se usó con otros datos/)).toBeVisible();
  await page.getByRole('button', { name: /iniciar un pago nuevo/i }).click();
  await fillForm(page, '12.50');
  await submit(page);
  await expect.poll(() => cap.length).toBe(2);
  expect(cap[1].key).not.toBe(cap[0].key);
});

test('FE-08 cambiar el monto tras un error genera clave nueva', async ({ page }) => {
  const cap = await stubCreate(page, (r, n) =>
    n === 1
      ? r.fulfill({ status: 503, json: errBody('BACKEND_DOWN', true) })
      : r.fulfill({ status: 201, json: okBody }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByLabel(/monto/i).fill('13.00');
  await submit(page);
  await expect.poll(() => cap.length).toBe(2);
  expect(cap[1].key).not.toBe(cap[0].key);
  expect(cap[1].body.amountCents).toBe(1300);
});

test('FE-09 fallo de red', async ({ page }) => {
  await stubCreate(page, (r) => r.abort('failed'));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByText(/No pudimos conectarnos/)).toBeVisible();
});

test('FE-10 no muestra message del servidor; si el traceId', async ({ page }) => {
  await stubCreate(page, (r) => r.fulfill({ status: 409, json: errBody('OTHER_CONFLICT', false) }));
  await open(page);
  await fillForm(page, '12.50');
  await submit(page);
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText('MENSAJE-INTERNO-XYZ')).toHaveCount(0);
  await expect(page.getByText('trace-abc-123')).toBeVisible();
});

test('FE-11 doble clic envia una sola peticion', async ({ page }) => {
  const cap = await stubCreate(page, async (r) => {
    await new Promise((res) => setTimeout(res, 500));
    await r.fulfill({ status: 201, json: okBody });
  });
  await open(page);
  await fillForm(page, '12.50');
  await page.getByRole('button', { name: /^pagar$/i }).dblclick();
  await expect(page.getByText('En proceso').first()).toBeVisible();
  expect(cap).toHaveLength(1);
});

test('FE-12 detalle: 404', async ({ page }) => {
  await page.route('**/api/v1/payments/*', (r) =>
    r.fulfill({ status: 404, json: errBody('X_TEST_CODE', false) }));
  await page.goto(`${BASE}${DETAIL_PATH(PAYMENT_ID)}`);
  await expect(page.getByText('No encontramos este pago. Verifica el identificador.')).toBeVisible();
});

const estados: Array<[string, string]> = [
  ['PENDING', 'En proceso'],
  ['CONFIRMED', 'Confirmado'],
  ['FAILED', 'No completado'],
  ['EXPIRED', 'Expirado'],
  ['REVERSED', 'Revertido'],
  ['ESTADO_DESCONOCIDO', 'Estado no reconocido'],
];
for (const [estado, texto] of estados) {
  test(`FE-13 detalle: ${estado} -> "${texto}"`, async ({ page }) => {
    await page.route('**/api/v1/payments/*', (r) =>
      r.fulfill({ status: 200, json: { ...okBody, status: estado } }));
    await page.goto(`${BASE}${DETAIL_PATH(PAYMENT_ID)}`);
    await expect(page.getByText(texto, { exact: true }).first()).toBeVisible();
  });
}
```

---

## 16. Requisitos pendientes consolidados

1. Criterios de aceptación oficiales del Issue.
2. Esquema de Datos (DDL), con nombres de tablas y columnas, y mecanismo de migración.
3. Contrato completo del BFF: cuerpo y respuesta de `POST /payments` y `GET /payments/{id}`, formato del `paymentId`, límite máximo de monto y lista de `code`.
4. `bcp-wallet-mock-openapi.yaml`.
5. Adaptador #9 o doble de `WalletBackendPort`, para arrancar el BFF y probar CA-11, CA-12 e INT-P1.
6. Decisión de autenticación (CA-13).
7. Comandos reales de build y arranque de #8.
8. Confirmación de dependencias de prueba instaladas (`junit-jupiter`, Jackson, driver PostgreSQL, `@playwright/test`).
9. Coordinación del uso del grupo 1 del mock.

**Nada de lo anterior ha sido ejecutado ni verificado. Todos los resultados son NO EJECUTADO o NO VERIFICABLE.**