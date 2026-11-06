# Informe de seguridad (AppSec): MVP de wallet en Perú

**Fecha:** 2026-11-06
**Alcance:** épica `packages/epic/peru-wallet-mock-mvp` (historias #2–#7, fundación #8–#11), según los entregables de Arquitectura, Backend, Frontend y Pruebas.
**Rol:** Revisor de seguridad de aplicaciones

> **Limitaciones de la revisión (importante):**
> - Revisé **especificaciones, no código fuente**. Arquitectura, Backend, Frontend y Pruebas llegaron truncados, y no recibí el OpenAPI del mock ni el modelo de Datos.
> - Backend, Frontend y Pruebas declaran **NO EJECUTADO**. Nada de lo que aquí se marca como "sin hallazgo" está verificado en ejecución.
> - Los hallazgos se basan en lo que **la documentación dice o deja sin definir**. Cuando es una ausencia de control y no una falla demostrada, lo indico en la columna "Evidencia".
> - Los comandos de verificación son **propuestas** y no los he ejecutado. Los nombres de campo (`payeeId`) y de tablas son supuestos heredados del equipo.

---

## 1. Resumen

### 1.1 Activos y datos sensibles

| Activo | Dónde vive | Sensibilidad |
|---|---|---|
| Pagos (importe, destinatario, estado, `createdAt`) | BFF (persistencia propia) y mock | Alta (integridad financiera) |
| Claves de idempotencia y `requestHash` | BFF (tabla de idempotencia) | Media (control de duplicidad) |
| `backendPaymentId`, `payerWalletId` | BFF (no se exponen en `PaymentResponse`) | Media |
| Datos KYC (documentos; en el mock solo `TEST-*`) | Mock y progreso de onboarding en el BFF (#2, #11) | Alta cuando sean reales (datos personales) |
| Eventos analíticos del funnel (#11) | BFF | Media (riesgo de PII en los eventos) |
| Instantáneas de conciliación (#5) | BFF | Alta (información financiera operativa) |
| Datos del grupo 1 del mock | Mock compartido | Media (disponibilidad e integridad de pruebas) |

### 1.2 Puntos de entrada

| Entrada | Estado | Observación de seguridad |
|---|---|---|
| `POST /api/v1/payments` (BFF) | Implementado (sin ejecutar) | Mueve dinero. **Sin autenticación definida.** |
| `GET /api/v1/payments/{paymentId}` (BFF) | Implementado (sin ejecutar) | **Sin control de propiedad evidenciado.** |
| Formularios Angular `/pagos/nuevo`, `/pagos/:paymentId` | Implementado (sin ejecutar) | Validaciones de cliente solo como apoyo. |
| Header `Idempotency-Key` | Implementado | Validado con `^[A-Za-z0-9_-]{8,64}$`. |
| Mock `/control/events`, `/callbacks/payments` (polling) | No implementado (#9, sync) | Sin firmas ni autenticación. |
| Mock `POST /control/reset` | Existe en el mock | Borra todos los datos del grupo 1, sin autenticación. |
| Onboarding, QR/comercio, conciliación, reversos | **No implementados** | Riesgos prospectivos (ver §3). |

### 1.3 Conclusión ejecutiva

- **No recomiendo liberar a usuarios reales** hasta cerrar S-01 a S-04. El BFF **no tiene autenticación ni autorización definidas**, y el único corte vertical entregado ya mueve dinero.
- El mock no tiene autenticación ni firmas (hecho del Issue). Por eso **el BFF es el único punto de control de seguridad** y debe asumir esa responsabilidad completa.
- Los controles defensivos de diseño son buenos:
  - consultas parametrizadas;
  - errores sin trazas ni SQL;
  - el frontend no muestra el `message` del servidor;
  - la clave de idempotencia no se guarda en `localStorage`;
  - `PaymentResponse` no expone `backendPaymentId` ni `payerWalletId`.

  Todo esto está **pendiente de verificar en ejecución**.

**Conteo de hallazgos:** 1 Crítica · 3 Altas · 6 Medias · 1 Baja.

---

## 2. Hallazgos

### 2.1 Tabla resumen (ordenada por severidad)

| ID | Severidad | Hallazgo | Componente afectado | OWASP |
|---|---|---|---|---|
| S-01 | **Crítica** | Sin autenticación ni autorización en los endpoints financieros del BFF | `PaymentResource`, todo `/api/v1` | A01, A07 |
| S-02 | **Alta** | Sin control de propiedad en `GET /payments/{paymentId}` (IDOR) | `PaymentResource.get`, `PaymentService.getPayment` | A01 |
| S-03 | **Alta** | Eventos del mock consumidos sin autenticidad ni firma (suplantación de estado) | `EventSyncService`/scheduler, mock `/callbacks/payments`, `/control/events` | A08, A04 |
| S-04 | **Alta** | Riesgo de duplicidad o inconsistencia financiera en la ventana BFF↔mock | `PaymentService.createPayment`/`callBackend`, `IdempotencyRepository` | A04 |
| S-05 | Media | Clave de idempotencia única **global** (no ligada al usuario) y sin retención definida | `IdempotencyRepository.tryInsert` (`ON CONFLICT (idempotency_key)`) | A01, A04 |
| S-06 | Media | `requestHash` podría omitir el destinatario | `PaymentService.createPayment` (hash SHA-256) | A04, A08 |
| S-07 | Media | Validación numérica incompleta: coerción de `12.5`→`12` y límite no publicado | `CreatePaymentRequest`, `PaymentService` (límite) | A03, A04 |
| S-08 | Media | Sin límite de tasa ni antiabuso evidenciado | `POST /api/v1/payments` | A04, A05 |
| S-09 | Media | Sin pista de auditoría de operaciones financieras definida | BFF (pagos, reversos, conciliación) | A09 |
| S-10 | Media | `POST /control/reset` sin autenticación y compartido; riesgo de exposición o uso accidental | Mock grupo 1, adaptador #9, pruebas con agentes | A01, A05 |
| S-11 | Baja | Sin evidencia de análisis de dependencias (SCA) ni versiones fijadas | Quarkus (backend), Angular (frontend) | A06 |

---

### 2.2 Detalle de hallazgos

#### S-01: Sin autenticación ni autorización en el BFF (Crítica)
- **Descripción:** Backend declara CA-13 (401/403) como **"No cubierto"** y que el contrato no define autenticación del BFF. El mock tampoco autentica. `POST /api/v1/payments` crea pagos sin identificar al pagador. Frontend solo asume 401/403 sin respaldo.
- **Evidencia:** backend_spec §4 (CA-13) y §1 ("autenticación 401/403 … no implementado"); frontend_spec §5 ("Backend no definió autenticación").
- **Impacto:** cualquiera con acceso de red al BFF puede crear y consultar pagos. No hay forma de atribuir una operación a un usuario, ni de aplicar límites por usuario. Rompe la trazabilidad y habilita fraude.
- **Corrección:**
  1. Exigir autenticación en **todas** las rutas de `/api/v1` con deny-by-default. Usar OIDC/JWT con validación de firma, `iss`, `aud` y `exp`; en Quarkus, `quarkus-oidc` o `quarkus-smallrye-jwt`.
  2. Derivar la identidad del pagador **del token, nunca del cuerpo**.
  3. Definir roles/scopes (usuario, comercio, operaciones/conciliación).
  4. En Angular, no guardar el token en `localStorage`. Usar memoria o cookie `HttpOnly; Secure; SameSite`, con protección CSRF si hay cookies.
- **Verificación:**
  ```bash
  # Sin credenciales: debe ser 401 (en todas las rutas)
  curl -i -X POST "$BFF/api/v1/payments" \
    -H "Idempotency-Key: sec-test-0001" -H "Content-Type: application/json" \
    -d '{"payeeId":"merchant-demo","amountCents":1000,"currency":"PEN"}'
  curl -i "$BFF/api/v1/payments/00000000-0000-0000-0000-000000000000"
  # Con token válido pero sin el scope requerido: 403
  ```
  Añadir una prueba automatizada (RestAssured con y sin `@TestSecurity`) que recorra todas las rutas de `/api/v1` y falle si alguna responde distinto de 401 sin credenciales.

#### S-02: Sin control de propiedad en la consulta de pagos (Alta)
- **Descripción:** `GET /payments/{paymentId}` consulta por id. No hay evidencia de que se compruebe que el pago pertenece al solicitante (dependiente de S-01). El formato de `paymentId` (UUID) **no está confirmado** (H6 de Pruebas). Si fuera predecible, se podrían enumerar pagos.
- **Evidencia:** backend_spec §2 y §4 (CA-8: "404 si no existe", sin mención de propietario); testing_report H6.
- **Impacto:** divulgación de datos financieros de otros usuarios (importe, destinatario, estado).
- **Corrección:**
  - Guardar `owner_id` en el pago y filtrar en la consulta (`WHERE payment_id=? AND owner_id=?`).
  - Responder **404** (no 403) cuando no es del solicitante, para no revelar existencia.
  - Usar identificadores no secuenciales (UUIDv4/aleatorios).
  - Aplicar lo mismo a la futura vista de comercio (#4): un comercio solo ve sus cobros.
- **Verificación:** con dos usuarios A y B, A crea un pago y B hace `GET` de ese id. Esperado: **404**. Prueba de integración que lo automatice.

#### S-03: Eventos del mock sin autenticidad ni firma (Alta)
- **Descripción:** el Issue indica que **no hay webhooks push ni firmas**. Los estados se actualizan consumiendo `/control/events` y `/callbacks/payments` por polling, y el mock **no tiene autenticación**. Si el sincronizador confía en el contenido de los eventos, quien pueda escribir en el mock podría forzar transiciones, por ejemplo `CONFIRMED` sobre un pago `FAILED`. Hoy el sync no está implementado, así que esto es un requisito de diseño, no un defecto demostrado.
- **Evidencia:** Issue ("Brechas del contrato"); arquitectura §1.2 punto 4; backend_spec §1 ("job de sincronización … no implementado").
- **Impacto:** estados de pago falsificados, conciliación errónea, riesgo de entregar bienes sin pago real.
- **Corrección:**
  1. El sync debe **tratar los eventos como una pista**, no como verdad. Ante un evento, volver a consultar el pago en el backend y aplicar ese estado.
  2. Implementar una **máquina de estados** con transiciones válidas. Rechazar y registrar las inválidas (p. ej. `FAILED→CONFIRMED`, `CONFIRMED→PENDING`).
  3. Ignorar eventos de ids que el BFF no conoce.
  4. Deduplicar eventos con cursor persistido (`EventCursorRepositoryPort`).
  5. Cuando haya un riel real, exigir firma/HMAC y comparación en tiempo constante.
- **Verificación:** pruebas unitarias del `EventSyncService` con eventos de id desconocido, transición inválida y evento duplicado. Esperado: sin cambio de estado, con registro de advertencia (sin PII). Prueba de contrato: el estado final proviene de la consulta al backend.

#### S-04: Ventana de duplicidad o inconsistencia financiera (Alta)
- **Descripción:** el flujo "insertar primero" confirma la clave en `PROCESSING` **antes** de llamar al mock. Backend reconoce una "ventana" en CA-2 (sección 6, que no recibí). Casos de riesgo según el diseño:
  - (a) el BFF cae tras `tryInsert` y antes de llamar al mock, dejando la clave en `PROCESSING` indefinidamente;
  - (b) el mock acepta el pago pero el BFF no registra el resultado, por lo que el pago queda `PENDING`;
  - (c) no se asume idempotencia del mock (`[PENDIENTE-OPENAPI]`), por lo que un reintento interno podría duplicar el cargo.

  La duplicidad financiera es el riesgo número uno que declara la propia épica.
- **Evidencia:** arquitectura D4 y §1.2 punto 3; backend_spec §3 (pasos 4–7), CA-2 y CA-11.
- **Impacto:** doble cargo o pago sin registro. Pérdida financiera y de confianza.
- **Corrección:**
  - Nunca reintentar la llamada al mock automáticamente sin confirmar antes el estado del pago en el backend.
  - Propagar al mock una referencia única (si el OpenAPI lo permite) o buscar el pago por referencia antes de reintentar.
  - Añadir un job de recuperación que lleve a un estado resuelto las claves `PROCESSING` más antiguas que N segundos, consultando el backend.
  - Mantener `PENDING` visible y consultable (no inventar `FAILED` sin confirmación).
- **Verificación:**
  - Prueba con un `WalletBackendPort` falso que lanza una excepción **después** de simular éxito. Esperado: un único cargo y un estado recuperable.
  - Prueba de concurrencia (API-15 de Pruebas): 10 envíos concurrentes con la misma clave dan exactamente un `201`.
  - Consulta de control (nombres de tabla por confirmar): no debe haber claves en `PROCESSING` más antiguas que el umbral tras el job.

#### S-05: Clave de idempotencia global y sin retención definida (Media)
- **Descripción:** la unicidad es sobre `idempotency_key` solamente (`ON CONFLICT (idempotency_key)`). La clave no está ligada al usuario. Dos usuarios que coincidan en una clave interferirían entre sí. Con solicitud idéntica, un usuario podría recibir el pago de otro como "repetición" (200). Además, no se define el tiempo de retención de las claves. Frontend genera `pay-` + 32 hex en el cliente, y **no se especifica la fuente de aleatoriedad**.
- **Evidencia:** backend_spec §2 y §3 (paso 4); frontend_spec §2.
- **Impacto:** fuga entre usuarios y colisiones. Crecimiento no acotado de la tabla.
- **Corrección:**
  - Unicidad compuesta `UNIQUE (owner_id, idempotency_key)` y búsqueda por ambos campos.
  - Definir TTL de claves (p. ej. 24–72 h) con job de purga.
  - En el cliente, generar la clave con `crypto.randomUUID()` o `crypto.getRandomValues`, nunca con `Math.random`.
- **Verificación:** prueba con usuarios A y B usando la misma clave y la misma solicitud. Esperado: dos pagos independientes y ninguno ve el del otro. Revisión estática en el frontend: `grep -rn "Math.random" src/` sin resultados en la generación de claves.

#### S-06: `requestHash` podría omitir el destinatario (Media)
- **Descripción:** Pruebas señala (H10) que no consta si el hash incluye el destinatario. Si no lo incluye, reutilizar la clave con otro destinatario devolvería el pago original con 200 en lugar de 409.
- **Evidencia:** testing_report H10 y caso API-04.
- **Impacto:** el usuario cree haber pagado al destinatario B y el sistema devolvió el pago a A. Riesgo de integridad y confusión.
- **Corrección:** calcular el hash sobre una **forma canónica** de todos los campos con efecto financiero: `payerId`, `payeeId`, `amountCents`, `currency` y referencia.
- **Verificación:** caso API-04: misma clave con otro `payeeId` debe devolver **409 `IDEMPOTENCY_KEY_REUSED`**.

#### S-07: Validación numérica incompleta (Media)
- **Descripción:**
  - Pruebas advierte (H11) que Jackson podría aceptar `12.5` en un campo `Long` truncándolo a `12` (coerción por defecto). Esto contradice CA-5.
  - El límite máximo se valida en servidor, pero su valor y su código no están publicados (H8). Frontend no lo valida.
  - El nombre y obligatoriedad de `payeeId` están sin confirmar. No consta una validación del destinatario en servidor.
- **Evidencia:** testing_report H4, H8, H11; frontend_spec §2.
- **Impacto:** importes alterados silenciosamente, límites eludibles, destinatarios arbitrarios.
- **Corrección:**
  - Deshabilitar la coerción en Jackson (`DeserializationFeature.ACCEPT_FLOAT_AS_INT` en `false`, rechazar cadenas en campos numéricos). Activar `quarkus.jackson.fail-on-unknown-properties=true`.
  - Añadir `@Positive` y `@Max` (límite configurable por proveedor).
  - Validar `payeeId` contra una lista permitida o formato estricto.
  - Publicar el límite y su código de error.
- **Verificación:**
  ```bash
  # Todos deben dar 400 con formato de error unificado
  curl -i -X POST "$BFF/api/v1/payments" -H "Authorization: Bearer $T" -H "Idempotency-Key: sec-test-0002" \
    -H "Content-Type: application/json" -d '{"payeeId":"merchant-demo","amountCents":12.5,"currency":"PEN"}'
  # repetir con "1000" (string), -1, 0, 9223372036854775807, currency "USD", campo extra, payeeId inexistente
  ```

#### S-08: Sin límite de tasa ni antiabuso evidenciado (Media)
- **Descripción:** cada clave nueva inserta filas en la base de datos y dispara una llamada al mock. No hay mención de límites de tasa, topes por usuario/ventana ni protección contra ráfagas.
- **Evidencia:** ausencia en backend_spec y arquitectura (no hay requisito definido).
- **Impacto:** consumo de recursos, crecimiento de tablas, abuso del mock compartido, posible ataque de "card testing" con importes mínimos.
- **Corrección:** límite de tasa por usuario e IP en el gateway/ingress o en un filtro (HTTP 429 con `Retry-After`). Topes por usuario y por ventana de tiempo, y alertas por ráfagas.
- **Verificación:**
  ```bash
  for i in $(seq 1 100); do curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BFF/api/v1/payments" \
    -H "Authorization: Bearer $T" -H "Idempotency-Key: rl-test-$i-0000" -H "Content-Type: application/json" \
    -d '{"payeeId":"merchant-demo","amountCents":100,"currency":"PEN"}'; done | sort | uniq -c
  ```
  Esperado: aparecen respuestas `429` al superar el umbral.

#### S-09: Sin pista de auditoría definida (Media)
- **Descripción:** hay `traceId` y eventos analíticos, pero no se define un registro de auditoría de operaciones financieras (quién, qué, cuándo, resultado). La épica exige manejo operativo/legal de reversos y disputas.
- **Evidencia:** arquitectura (solo `traceId`, analytics); riesgos de la épica.
- **Impacto:** imposibilidad de investigar fraude o disputas. Incumplimiento operativo.
- **Corrección:** registro de auditoría append-only para crear/consultar/revertir pagos y conciliación, con actor, `paymentId`, resultado y `traceId`. No incluir documentos de identidad, tokens ni claves completas. Separar el logging de auditoría del logging técnico.
- **Verificación:** prueba de integración que, tras crear un pago, comprueba un registro de auditoría con los campos requeridos. Revisión de logs: `grep -iE "Authorization|Bearer|documentNumber" logs/` sin coincidencias.

#### S-10: `POST /control/reset` sin autenticación y compartido (Media)
- **Descripción:** el reset borra todos los datos del grupo 1, sin autenticación y compartido. Arquitectura (D8) dice que los usuarios no lo ven, pero no define cómo se impide que el adaptador de producción lo exponga. El Issue pide coordinar su uso en pruebas con agentes.
- **Evidencia:** Issue ("Brechas del contrato"); arquitectura D8.
- **Impacto:** pérdida de datos de pruebas, corrupción de conciliación y estados. Riesgo si el perfil de producción apunta al mock.
- **Corrección:**
  - El método de reset no debe existir en el puerto `WalletBackendPort` de producción. Solo en un perfil de pruebas.
  - No exponer ninguna ruta del BFF que lo proxee.
  - Documentar y proteger el uso en `AGENTS.md` (#10), con aviso previo al equipo.
- **Verificación:** `grep -rn "control/reset" src/main` debe devolver 0 resultados. En CI, verificar que el BFF no tiene rutas que lo expongan (`curl -i -X POST "$BFF/control/reset"` → 404/401).

#### S-11: Dependencias sin análisis (Baja)
- **Descripción:** se asumen extensiones Quarkus (`quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-agroal`, `quarkus-jdbc-postgresql`, `quarkus-narayana-jta`) sin versiones fijadas, y no hay evidencia de SCA. No identifiqué CVE específicos; es una brecha de proceso.
- **Evidencia:** backend_spec §1 ("stack final depende de #8").
- **Impacto:** exposición a vulnerabilidades conocidas de librerías.
- **Corrección:** fijar versiones (BOM de Quarkus, `package-lock.json`) y añadir SCA y escaneo a CI.
- **Verificación:**
  ```bash
  mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
  npm ci && npm audit --audit-level=high
  ```

---

### 2.3 Áreas revisadas sin hallazgo (según la especificación; sin ejecutar)

| Área | Conclusión | Cómo confirmarlo |
|---|---|---|
| Inyección SQL | Sin hallazgo: JDBC con `PreparedStatement` y `ON CONFLICT` parametrizado; `RepositoryException` no expone SQL. | `grep -rnE "Statement\(|\+ *\"|String.format.*(SELECT\|INSERT\|UPDATE)" src/main`: no debe haber concatenación de SQL. Prueba con `Idempotency-Key` y campos con `'; --`. |
| XSS (frontend) | Sin hallazgo: Angular con binding, y el cliente **no muestra `message` del servidor**; solo mapea por `code`/HTTP. | `grep -rnE "innerHTML\|bypassSecurityTrust" src/` sin resultados. |
| Filtración de información en errores | Sin hallazgo: `ErrorMappers` devuelve `code/message/retryable/traceId` sin trazas ni SQL. | Provocar un error de BD y verificar que la respuesta no incluye stacktrace. |
| Validación de la clave de idempotencia | Sin hallazgo: regex estricta `^[A-Za-z0-9_-]{8,64}$`, lo que también limita la inyección en logs. | Casos API-05..07 de Pruebas. |
| Almacenamiento de la clave en cliente | Sin hallazgo: solo en memoria, nunca en `localStorage`. | Revisión del servicio de pagos en Angular. |
| Secretos y credenciales | Sin hallazgo en el material recibido: la URL del mock no es secreta y el mock no usa credenciales. **Pendiente** de definir las credenciales de BD y futuras claves del BFF. | `git grep -nEi "password\|secret\|apikey\|token" -- . ':!*.lock'`; ver recomendaciones. |
| Exposición de campos internos | Sin hallazgo: `PaymentResponse` excluye `backendPaymentId` y `payerWalletId`. | Comparar el JSON de respuesta con la lista de campos. |

### 2.4 Riesgos prospectivos (funcionalidad aún no implementada)

No son hallazgos, pero deben incorporarse como requisitos antes de implementar cada historia:

- **#2 Onboarding / #11 persistencia:** los datos KYC serán datos personales (en Perú aplica la Ley 29733 de Protección de Datos Personales). No guardar documentos en claro ni en eventos analíticos. Minimizar, cifrar y definir retención. En el mock se usan solo `TEST-*`.
- **#4 Vista de comercio:** autorización por comercio (ver S-02). El mock solo conoce `merchant-demo`.
- **#5 Conciliación:** acceso restringido a rol operativo. Las instantáneas son datos financieros.
- **#7 Reversos:** exigir rol, idempotencia propia y auditoría. No exponer reversos sin que el mock los ofrezca (D6).
- **#9 Adaptador:** TLS con verificación de certificado, timeouts de conexión y lectura, sin seguir redirecciones, URL base desde configuración, y manejo de `BackendUnavailableException` sin filtrar detalles.

---

## 3. Criterios de aceptación: riesgos y faltantes de seguridad

El Issue no trae criterios numerados. Sobre los derivados (CA-1…CA-13):

| Criterio | Evaluación de seguridad |
|---|---|
| CA-1 a CA-4 (idempotencia) | Correctos como base, pero **insuficientes**: faltan el alcance por usuario (S-05), el hash completo (S-06) y la recuperación de `PROCESSING` (S-04). |
| CA-5 (importes) | Falta el valor del límite, rechazo de coerción y tope máximo en el esquema (S-07). |
| CA-8 (consulta por id) | **Riesgoso tal como está**: no exige propiedad (S-02). |
| CA-10 (errores) | Adecuado. |
| CA-11, CA-12 | Dependen del sync y de la autenticidad de eventos (S-03). |
| CA-13 (401/403) | **Marcado como no cubierto; debe ser bloqueante** (S-01). |

**Criterios de seguridad que faltan y deben añadirse:**
1. Toda ruta de `/api/v1` exige autenticación y autorización (deny-by-default).
2. Un usuario solo puede ver y operar sus propios pagos y cobros.
3. Límite de tasa y topes por usuario en la creación de pagos.
4. Los eventos externos no cambian el estado sin verificación y sin transición válida.
5. Los logs y eventos analíticos no contienen PII, tokens ni claves completas.
6. Registro de auditoría de operaciones financieras.
7. Retención y purga de claves de idempotencia.
8. `/control/reset` inaccesible desde el BFF de producción.
9. Dependencias sin vulnerabilidades altas/críticas conocidas (SCA en CI).
10. CORS restrictivo (solo el origen del frontend) y cabeceras de seguridad (`X-Content-Type-Options`, `Content-Security-Policy`, `Strict-Transport-Security`). La documentación no los define.

---

## 4. Recomendaciones (priorizadas)

**Antes de cualquier uso con datos reales (bloqueantes)**
1. **S-01:** implementar autenticación (OIDC/JWT) y autorización por rol, y derivar el pagador del token.
2. **S-02:** guardar `owner_id` y filtrar consultas por propietario. Responder 404 cuando no pertenece.
3. **S-03:** el sync debe confirmar estados consultando al backend y aplicar una máquina de estados estricta.
4. **S-04:** diseñar la recuperación de `PROCESSING`/`PENDING` y no reintentar al mock sin confirmar el estado.

**Siguiente iteración**
5. **S-05/S-06:** unicidad `(owner_id, idempotency_key)`, hash canónico completo, TTL y purga. Generar claves con `crypto.randomUUID()`.
6. **S-07:** endurecer Jackson, publicar límites y validar `payeeId`.
7. **S-08:** límite de tasa y topes por usuario.
8. **S-09:** auditoría append-only sin PII.
9. **S-10:** aislar el reset en un perfil de pruebas y documentarlo en `AGENTS.md`.
10. **S-11:** SCA en CI con umbral de fallo.

**Procesos y plantillas (#10)**
- La plantilla de Pull Request debe incluir un checklist de seguridad: authN/authZ, validación, secretos, logs sin PII y dependencias.
- `AGENTS.md` debe prohibir confirmar secretos y exigir coordinación antes de usar `/control/reset`.

**Secretos**
- Credenciales de BD, claves de firma y URL del mock por variables de entorno o gestor de secretos, nunca en el repositorio. Habilitar escaneo de secretos (por ejemplo `gitleaks detect`) en CI.

---

## 5. Checklist de verificación

Marcar solo con evidencia de ejecución (hoy todo está pendiente).

**Autenticación y control de acceso**
- [ ] Todas las rutas de `/api/v1` responden 401 sin credenciales (S-01).
- [ ] Un token sin el rol/scope requerido recibe 403 (S-01).
- [ ] El usuario B no puede consultar el pago del usuario A (404) (S-02).
- [ ] El pagador se toma del token, no del cuerpo (S-01).
- [ ] La futura vista de comercio solo muestra cobros del comercio autenticado (S-02).

**Idempotencia e integridad financiera**
- [ ] Misma clave y misma solicitud: 200 con el mismo `paymentId` (CA-2).
- [ ] Misma clave con distinto monto **y** con distinto destinatario: 409 (S-06).
- [ ] 10 envíos concurrentes con la misma clave: exactamente un 201 (S-04).
- [ ] La misma clave en usuarios distintos no interfiere (S-05).
- [ ] Hay recuperación comprobada de claves en `PROCESSING` antiguas (S-04).
- [ ] Hay purga de claves según el TTL (S-05).

**Eventos y estados**
- [ ] El sync rechaza eventos de ids desconocidos, transiciones inválidas y duplicados (S-03).
- [ ] El estado final proviene de la consulta al backend (S-03).

**Validación de entradas**
- [ ] `amountCents` = `12.5`, `"1000"`, `0`, negativo, desbordado, o `currency` ≠ `PEN`: 400 (S-07).
- [ ] Campos desconocidos en el cuerpo: 400 (S-07).
- [ ] `payeeId` fuera de la lista permitida: 400 (S-07).
- [ ] Clave de idempotencia fuera de patrón: 400 (CA-1).

**Errores, logs y datos**
- [ ] Ninguna respuesta de error contiene trazas ni SQL (CA-10).
- [ ] Logs sin tokens, documentos ni claves completas (S-09).
- [ ] Existe un registro de auditoría por operación financiera (S-09).
- [ ] Eventos analíticos sin PII (#11).

**Abuso y disponibilidad**
- [ ] El límite de tasa devuelve 429 al superar el umbral (S-08).

**Mock y entorno**
- [ ] `grep -rn "control/reset" src/main` sin resultados; el BFF no expone esa ruta (S-10).
- [ ] El adaptador usa TLS verificado, timeouts y no sigue redirecciones (#9).

**Frontend**
- [ ] Sin `innerHTML` ni `bypassSecurityTrust*` (sección 2.3).
- [ ] La clave de idempotencia se genera con `crypto` y no se guarda en `localStorage` (S-05).
- [ ] Tokens fuera de `localStorage`.
- [ ] CORS restrictivo y cabeceras de seguridad presentes.

**Dependencias y secretos**
- [ ] `mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7` sin fallos (S-11).
- [ ] `npm audit --audit-level=high` sin hallazgos (S-11).
- [ ] Escaneo de secretos en CI sin hallazgos.

---

**Estado final de esta revisión:** basada únicamente en documentación; sin ejecución de código, pruebas ni llamadas al mock. Los hallazgos S-01 a S-04 deben resolverse antes de cualquier despliegue con usuarios reales. Una vez entregado el código, el OpenAPI del mock y las secciones truncadas de Backend (§5–6) y Frontend (§8), debo repetir la revisión sobre el código fuente.