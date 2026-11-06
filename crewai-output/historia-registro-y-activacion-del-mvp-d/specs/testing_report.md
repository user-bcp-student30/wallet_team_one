# testing_report.md

**Issue:** Historia «Registro y activación del MVP de wallet» (Épica #1, tareas #12 BFF, #13 UI, #14 Analítica)
**Fecha del informe:** 2026-11-06
**Responsable:** Ingeniería de pruebas

> **Declaración de evidencia.** No ejecuté ningún comando, prueba ni herramienta. Todo caso de este informe está en estado **NO EJECUTADO**, con resultado esperado únicamente como expectativa. No hay salidas reales que reportar.
> Los casos que no se pueden escribir como código porque falta un artefacto aprobado están **BLOQUEADOS**. El motivo y el requisito pendiente figuran en cada caso.

---

## 1. Resumen del Issue

El usuario de smartphone con transferencias diarias de bajo monto debe completar el onboarding de la wallet con un flujo guiado y señales claras de siguiente paso.

| # | Criterio de aceptación |
|---|---|
| C1 | El flujo muestra la secuencia de pasos requeridos y el paso actual en todo momento. |
| C2 | Si falta información obligatoria o hay datos inválidos, el flujo bloquea el avance y muestra un mensaje accionable. |
| C3 | Al finalizar, la wallet pasa a activa y el usuario accede al punto de entrada de pagos de bajo monto. |
| C4 | Se registran eventos de inicio de onboarding, fin de cada paso y cierre del flujo. |
| C5 | Cohorte MVP Perú: mediana de onboarding de 8 a 3 minutos dentro del trimestre posterior al lanzamiento. |
| C6 | La salida analítica incluye conteos mensuales de nuevos usuarios activos y atribución de gasto de adquisición para verificar MAU (1,000-2,000) y CAC (PEN 10) en 3 meses. |

**Preguntas abiertas del Issue (afectan a las pruebas):**
- Frontera de eventos que define la duración del onboarding.
- Requisitos de KYC y documentación, aún por definir con Cumplimiento y Producto.

---

## 2. Contraste entre lo requerido, lo definido y lo implementado

### 2.1 Artefactos recibidos y su completitud

| Artefacto | Estado | Consecuencia para las pruebas |
|---|---|---|
| Arquitectura (`user_story.md`) | Truncado después de `E1 … 201 Created`. | Contratos de E1 (resto de respuestas) y de los demás endpoints no disponibles. |
| Backend (`backend_spec.md`) | Solo **E1 `POST /onboarding/sessions`**. Código Java visible: enums, records, DTOs, excepciones. `OnboardingSessionRepository` truncado. `OnboardingResource`, `OnboardingService`, assembler, catálogo y mappers solo por nombre. | Solo E1 es probable con caja negra. Las firmas internas no se conocen y no escribo pruebas que las supongan. |
| Datos | **No recibido.** El esquema `onboarding_session` es un supuesto de Backend. | Las pruebas de persistencia dependen de un esquema no aprobado. |
| Frontend (`frontend_spec.md`) | Solo el spec, con textos, roles ARIA y comportamientos documentados. Truncado en la sección 5. Código del componente no visible. | UI probable solo como caja negra en el navegador, con E1 simulado. |
| Repositorio, `pom.xml`/`build.gradle`, `package.json`, framework de pruebas | **No vistos.** | Framework y dependencias son requisitos pendientes. |

### 2.2 Hallazgos (discrepancias y faltantes)

| ID | Hallazgo | Impacto |
|---|---|---|
| H-01 | Los contratos de consulta de progreso, envío de paso, completar/activar y reporte no existen en los artefactos recibidos. Backend y Frontend no implementaron esas operaciones. | C2 (bloqueo en servidor), C3 y C4 (parcial) no son verificables extremo a extremo. Casos BL-01 a BL-04. |
| H-02 | El contrato de `POST /kyc/verifications` (body, respuestas, errores) no está documentado. | BL-02/BL-09: no se puede verificar la integración con KYC. |
| H-03 | `currentStepIndex` es ambiguo. El ejemplo del contrato muestra `2` con `IDENTITY_DOCUMENT` (orden 2, lo que sugiere base 1). Backend dice «crea una sesión en el paso 1». | Mis pruebas **no afirman** su valor numérico. Afirman `currentStepId` y los estados de `steps[]`. Debe aclararse (Arquitectura). |
| H-04 | Del C4 solo está implementado `onboarding_started` (Backend). No hay fin de paso ni cierre del flujo. | BL-04 bloqueado. `onboarding_started` tampoco es observable desde fuera (BL-05). |
| H-05 | La semántica de `Idempotency-Key` (reproducción de la misma respuesta, ventana de validez) no está definida. | AP-03 solo afirma que `sessionId` es igual y que el status es 200 o 201. |
| H-06 | Los valores de `ErrorResponse.code` no están en el contrato. | Las pruebas exigen que `code` no esté vacío, no un valor concreto. |
| H-07 | El mecanismo de autenticación real y cómo obtener tokens de prueba no están confirmados. | Los tokens y los `userId` de prueba se inyectan por propiedades. |
| H-08 | El criterio C5 (8→3 min) y el C6 (MAU 1,000-2,000, CAC PEN 10) son resultados de negocio. Datos (#14) no entregó definiciones, consultas ni salida. Arquitectura define frontera «server timestamps», pero la pregunta abierta del Issue sigue sin cierre. | No se pueden verificar con los artefactos. BL-06/BL-07 solo documentan qué falta. |
| H-09 | Que la respuesta 200 en reanudación y los códigos 400/401 de E1 provienen de Backend/Frontend, no del contrato de Arquitectura visible (truncado). | Las pruebas de E1 asumen la descripción de Backend. Debe confirmarse contra el contrato completo. |
| H-10 | Observación de código: `OnboardingValidationException.getFieldErrors()` devuelve el mapa interno mutable. El constructor sí copia defensivamente. | Informativo, sin prueba. Posible mejora (devolver copia no modificable). |
| H-11 | Observación: el código de onboarding vive en `pe.bcp.login.*`. No se verificó si ese es el paquete correcto del proyecto. | Informativo. |
| H-12 | La ruta del punto de entrada de pagos de bajo monto es desconocida («existencia y ruta no verificadas»). El frontend usa configuración local. | UI-08 solo verifica que no se navega a un destino enviado por el servidor. |

---

## 3. Matriz de trazabilidad

Estado: **NO EJECUTADO** (propuesta con código, sin salida real) o **BLOQUEADO** (sin artefacto para escribir la prueba).

| Criterio | Caso | Tipo | Resultado esperado (resumen) | Estado |
|---|---|---|---|---|
| C1 | AP-01 | Positivo/Integración | E1 crea sesión: 201, modelo de progreso válido, 1 paso `CURRENT`, resto `PENDING`. | NO EJECUTADO |
| C1 | AP-02 | Positivo/Integración | Reanudar devuelve 200 y el mismo `sessionId`. | NO EJECUTADO |
| C1 | AP-12 | Positivo (provisional) | Catálogo `PERSONAL_DATA`, `IDENTITY_DOCUMENT`, `CONFIRMATION`, todos requeridos. | NO EJECUTADO |
| C1 | UI-01, UI-02 | Positivo | Indicador `nav>ol`, un `aria-current="step"`, «Paso X de N», texto accesible por estado. | NO EJECUTADO |
| C1 | UI-04, UI-05 | Borde | Estado de carga y estado vacío visibles. | NO EJECUTADO |
| C1 | UI-12 | Positivo | Foco en el encabezado tras cargar. | NO EJECUTADO |
| C1 | BL-01 | Integración | Consulta de progreso tras cerrar y reabrir la app. | BLOQUEADO (H-01) |
| C2 | UT-01…UT-04 | Borde/Negativo | `acquisitionChannel`: null, vacío y 50 válidos; 51 inválido con mensaje. | NO EJECUTADO |
| C2 | AP-04, AP-05, AP-14 | Borde/Negativo | 50 → 201; 51 → 400 con `fieldErrors`; `{}` → 201. | NO EJECUTADO |
| C2 | AP-06, AP-07, AP-13 | Negativo | Sin clave, clave no UUID o JSON malformado → 400 sin efectos ni fuga de detalles. | NO EJECUTADO |
| C2 | UI-03, UI-06 | Negativo | Sin definición de campos, «Continuar» deshabilitado; errores mapeados por HTTP sin fuga. | NO EJECUTADO |
| C2 | BL-02 | Negativo/Integración | Bloqueo de avance en servidor y errores accionables por campo en el envío de paso. | BLOQUEADO (H-01, H-02) |
| C3 | UI-08, UI-09, UI-10 | Positivo/Negativo/Seguridad | Botón «Ir a pagos de bajo monto» solo con `COMPLETED`+`ACTIVE`; no se navega a un `target` externo. | NO EJECUTADO |
| C3 | BL-03 | Integración | Wallet `ACTIVE` persistida y acceso real a pagos. | BLOQUEADO (H-01, H-12) |
| C4 | BL-05 | Integración | `onboarding_started` se emite una vez en creación y no en reanudación. | BLOQUEADO (evento no observable) |
| C4 | BL-04 | Integración | Eventos de fin de paso y cierre de flujo. | BLOQUEADO (H-04) |
| C5 | BL-07 | Integración | Mediana de duración con frontera de eventos definida. | BLOQUEADO (H-08) |
| C6 | BL-06 | Integración | Reporte de MAU y CAC con fuente de gasto de adquisición. | BLOQUEADO (H-08) |
| Contratos E1 | AP-03, AP-08, AP-09, AP-10, AP-11 | Seguridad/Integración | Idempotencia, 401, `userId` del token, correlación saneada, concurrencia. | NO EJECUTADO |
| Persistencia | PE-01…PE-06 | Integración | Fila correcta, sin duplicados, índice único parcial, nueva sesión tras `COMPLETED`. | NO EJECUTADO |
| Correcciones/compromisos | UI-07, UI-11, UT-05…UT-07 | Seguridad/Borde | Reintento con misma clave, nueva clave en «Empezar de nuevo», sin XSS, copia defensiva. | NO EJECUTADO |

**Tipos que no aplican o son parciales:**
- **Borde/integración para C5 y C6.** Son metas de negocio sobre una cohorte y un trimestre. No se prueban funcionalmente con datos sintéticos de forma significativa. Solo se podrán verificar con la salida real de #14 (BL-06/BL-07).
- **Unitarias del servicio, ensamblador y catálogo.** No se escriben porque sus firmas no son visibles. Se cubren por caja negra (AP-*).

---

## 4. Catálogo de casos (precondiciones, pasos, datos, resultado esperado)

Datos comunes:
- **Usuario A y usuario B:** usuarios de prueba con token válido y sin sesiones previas.
- **Clave:** `Idempotency-Key` = UUID v4 nuevo por llamada, salvo indicación.
- Todos los casos están **NO EJECUTADO**.

### 4.1 Unitarias (sin servicio ni BD)

| ID | Tipo | Precondiciones | Pasos | Datos | Esperado |
|---|---|---|---|---|---|
| UT-01 | Borde | Validador Jakarta con `ParameterMessageInterpolator` | Validar `StartOnboardingRequest` | `acquisitionChannel=null` | 0 violaciones |
| UT-02 | Borde | Ídem | Validar | `"a"×50` | 0 violaciones |
| UT-03 | Negativo/Borde | Ídem | Validar | `"a"×51` | 1 violación en `acquisitionChannel`, mensaje «El canal de adquisición no puede superar 50 caracteres.» |
| UT-04 | Borde | Ídem | Validar | `""` | 0 violaciones |
| UT-05 | Borde/Corrección | Ninguna | Crear `OnboardingValidationException` con un mapa; mutar el mapa original | `{b:2,a:1}` y luego `c:3` | La excepción conserva solo `b,a` y en ese orden |
| UT-06 | Positivo | Ninguna | `new StorageException("m", causa)` | `causa=RuntimeException("x")` | `getCause()` es la causa; mensaje `m` |
| UT-07 | Positivo | Ninguna | `new DuplicateSessionException("dup")` | n/a | Mensaje `dup`; es `RuntimeException` |

### 4.2 API E1 `POST /onboarding/sessions` (caja negra)

Precondiciones: servicio en marcha, `onboarding.baseUrl`, tokens A y B, `userId` A y B, y BD de pruebas configurada para limpieza (ver §6).

| ID | Tipo | Pasos | Datos | Esperado |
|---|---|---|---|---|
| AP-01 | Positivo | POST sin body, usuario A sin sesión | Token A, clave nueva | **201**, JSON; `sessionId` UUID; `status=IN_PROGRESS`; `totalSteps==steps.size()`; órdenes crecientes; un solo `CURRENT` igual a `currentStepId` y es el primero; resto `PENDING`; `walletStatus=PENDING`; `nextAction.type=CONTINUE_STEP`; `version` entero |
| AP-02 | Positivo/Integración | 2 POST consecutivos con claves distintas | Token A | 1.º 201; 2.º **200**; mismo `sessionId` |
| AP-03 | Positivo | 2 POST con la misma clave | Token A | Mismo `sessionId`; status ∈ {200,201} (H-05) |
| AP-04 | Borde | POST con canal de 50 caracteres | `{"acquisitionChannel":"a"×50}` | **201** |
| AP-05 | Borde/Negativo | POST con 51 caracteres | `"a"×51` | **400**; `code`, `message`, `correlationId` no vacíos; `fieldErrors` contiene un campo con `acquisitionChannel`; sin fuga; 0 filas creadas |
| AP-06 | Negativo | POST sin `Idempotency-Key` | Token A | **400**; 0 filas creadas |
| AP-07 | Negativo | POST con clave no UUID | `no-es-uuid`, `123`, `00000000-0000-0000-0000-00000000000`, `' OR 1=1 --` | **400**; sin fuga |
| AP-08a/b | Negativo/Seguridad | POST sin `Authorization` / con token inválido | clave válida | **401**; sin fuga |
| AP-09 | Seguridad | POST con token A y `{"userId":"<B>"}` | Body con `userId` de B | Status ∈ {200,201,400}. Si es 2xx: la fila pertenece a A, B no tiene filas |
| AP-10 | Seguridad | Provocar 400 (sin clave) con `X-Correlation-Id` ausente, UUID y `<script>…` | Cabeceras variadas | `correlationId` no vacío siempre. Con UUID, igual al enviado (**inferencia**: no es seguro que UUID sea «válido» para Backend). Con hostil, no contiene `<script>` |
| AP-11 | Integración/Concurrencia | 8 POST simultáneos, claves distintas, usuario B sin sesión | Token B | Todos 200/201; exactamente un 201; un único `sessionId`; 1 fila en BD |
| AP-12 | Positivo (provisional) | POST usuario A | Token A | `steps` = `PERSONAL_DATA`, `IDENTITY_DOCUMENT`, `CONFIRMATION`, todos `required=true`. **Catálogo provisional**, no confirmado por Cumplimiento |
| AP-13 | Negativo | POST con JSON malformado | `{"acquisitionChannel":` | **400** (el contrato no lo especifica; Backend declara 400 por validación); sin fuga |
| AP-14 | Borde | POST con `{}` | Token A | **201**, igual que sin body |

### 4.3 Persistencia (supuesto de esquema: sin confirmar por Datos)

Tabla `onboarding_session(id, user_id, status, current_step_index, version, wallet_status, acquisition_channel, started_at, updated_at)` e índice único parcial sobre `(user_id)` donde `status='IN_PROGRESS'`.

| ID | Tipo | Pasos | Esperado |
|---|---|---|---|
| PE-01 | Positivo | POST con `acquisitionChannel="web-test"`; leer fila por `id` | `user_id`=A, `status`=IN_PROGRESS, `wallet_status`=PENDING, `acquisition_channel`=web-test, `started_at` y `updated_at` no nulos |
| PE-02 | Integridad | 2 POST seguidos; contar filas de A | Exactamente 1 fila |
| PE-03 | Integridad/Negativo | Insertar copia IN_PROGRESS de la fila de A | `SQLException` con SQLState **23505**; sigue 1 IN_PROGRESS |
| PE-04 | Integridad/Borde | Marcar la fila COMPLETED; insertar copia IN_PROGRESS | Inserción exitosa; 2 filas; 1 IN_PROGRESS |
| PE-05 | Integridad | A y B crean sesión | Una IN_PROGRESS cada uno; `id` distintos |
| PE-06 | Integración | Crear sesión de A, marcar COMPLETED por SQL, POST de nuevo | **201** con `sessionId` distinto |

Riesgo conocido de PE-04/PE-06: si el esquema real tiene CHECK que impide `COMPLETED` con `wallet_status=PENDING`, el `UPDATE` fallará. Eso sería un hallazgo sobre el esquema, no necesariamente un defecto.

Restricciones **no verificables** (no definidas): NOT NULL, CHECK, tipos, claves foráneas, política de borrado, índices adicionales.

### 4.4 UI (Playwright, E1 simulado con `page.route`)

Precondiciones: aplicación Angular servida, `ONBOARDING_URL` apunta a la pantalla de onboarding. Las respuestas de E1 se simulan, por lo que estos casos no prueban el backend real.

| ID | Tipo | Esperado |
|---|---|---|
| UI-01 | Positivo | Con paso 2 actual: `nav ol li` = 3; un único `[aria-current="step"]` con «Documento de identidad»; texto «Paso 2 de 3». |
| UI-01b | Corrección SP-5 | Con `currentStepIndex=99` inconsistente, la UI muestra «Paso 2 de 3» (usa `steps[].status`). |
| UI-02 | Positivo/A11y | El indicador contiene los textos «completado», «paso actual», «pendiente». |
| UI-03 | Negativo | **Condicional** (`STEP_FIELDS_EMPTY=true`): «Este paso todavía no está disponible» y «Continuar» deshabilitado. |
| UI-04 | Borde | Con respuesta demorada aparece «Cargando tu registro…» y luego desaparece. |
| UI-05 | Borde | Con `steps=[]`: «No hay pasos disponibles por ahora» y botón «Reintentar». |
| UI-06 | Negativo | 0 (aborto), 400, 401, 403, 404, 409, 500 y 503 → título y acción documentados; nunca se muestra el cuerpo crudo (`PSQLException`, `pe.bcp`). |
| UI-07a | Corrección | Primera petición con `Idempotency-Key` UUID; tras 503, «Reintentar» reutiliza la misma clave. |
| UI-07b | Corrección | En `CLOSED_INCOMPLETE`, «Empezar de nuevo» usa una clave distinta. |
| UI-08 | Seguridad | Con `COMPLETED`+`ACTIVE` aparece «Ir a pagos de bajo monto». Con `target` externo no hay navegación ni petición a ese host. |
| UI-09 | Negativo | `COMPLETED` con `walletStatus=PENDING`: no aparece «Ir a pagos de bajo monto». |
| UI-10 | Positivo | `CLOSED_INCOMPLETE`: aparece «Empezar de nuevo». |
| UI-11 | Seguridad | Una etiqueta con `<img onerror>` no ejecuta código (sin `innerHTML`). |
| UI-12 | A11y | Tras cargar, el foco está en un elemento con `tabindex="-1"` (encabezado). |

### 4.5 Casos BLOQUEADOS

| ID | Qué verificaría | Qué falta |
|---|---|---|
| BL-01 | Consulta de progreso; persistencia del paso actual al cerrar y reabrir. | Contrato del GET (H-01). |
| BL-02 | Envío de paso: bloqueo en servidor, errores por campo, orden de pasos, llamada a `POST /kyc/verifications`. | Contrato y código de envío de paso; contrato KYC; campos requeridos (H-01, H-02). |
| BL-03 | Completar onboarding, wallet `ACTIVE` persistida y `nextAction=GO_TO_LOW_AMOUNT_PAYMENTS`. | Contrato y servicio dueño del estado de wallet. |
| BL-04 | Eventos de fin de paso y de cierre de flujo con timestamps de servidor. | Implementación y esquema del evento (H-04). |
| BL-05 | `onboarding_started` emitido una vez en creación y ninguno en reanudación. | Forma de observar el evento (clase `OnboardingEvent` truncada; bus, topic o tabla no definidos). Diseño listo: un observador CDI en prueba de integración, o consulta del destino de eventos. |
| BL-06 | Reporte mensual: nuevos usuarios activos, gasto atribuido, CAC. | Contrato del reporte, fuente de gasto de adquisición, definiciones validadas (H-08). |
| BL-07 | Mediana de duración con frontera inicio/fin. | Respuesta a la pregunta abierta; datos y consulta de Datos (H-08). |
| BL-08 | Contraste AA, lector de pantalla, teclado en dispositivo real. | Verificación manual o con axe en un entorno real (Frontend declaró no haber medido contraste). |
| BL-09 | Contrato de `POST /kyc/verifications` (esquema, errores, timeouts). | Documentación del endpoint existente. |

---

## 5. Revisión de contratos y correcciones propuestas

### 5.1 Contrato de API (solo E1)

| Elemento | Fuente | Cobertura | Observación |
|---|---|---|---|
| `POST /onboarding/sessions` | Arquitectura (visible) | AP-01…AP-14 | Resto de respuestas de E1 truncado (H-09). |
| `Idempotency-Key` obligatorio y UUID | Arquitectura/Backend | AP-03, AP-06, AP-07 | Semántica de replay indefinida (H-05). |
| `acquisitionChannel` opcional, máx. 50 | Arquitectura/Backend | UT-01…04, AP-04, AP-05, AP-14 | Campo en `fieldErrors` puede llevar prefijo. Se afirma «contiene». |
| `userId` solo del token | Arquitectura/Backend | AP-09, PE-01 | |
| 201 crea / 200 reanuda | Backend/Frontend | AP-01, AP-02 | Pendiente de contrato completo. |
| 400 validación, 401 auth, 500 genérico | Backend | AP-05…AP-08, AP-13 | El 500 no se puede forzar sin inyectar fallos. **No cubierto**. |
| `ErrorResponse{code,message,correlationId,fieldErrors}` | Backend | AP-05, AP-06, AP-10 | `code` sin valores definidos (H-06). |
| Modelo de progreso §3.2 | Arquitectura | AP-01, AP-12, UI-* | `currentStepIndex` ambiguo (H-03). |
| `GET`/envío de paso/completar/reporte | Arquitectura (truncado) | Ninguna | BLOQUEADO. |

### 5.2 Persistencia

Hay cobertura de la tabla supuesta y del índice único parcial (PE-01…PE-06). **No hay** modelo de Datos recibido, y no puedo marcar como «aprobada» ninguna restricción. No hay migraciones ni tabla de eventos para revisar.

### 5.3 Correcciones y compromisos de etapas previas

No recibí una lista formal de correcciones. Verifico los compromisos declarados:

| Compromiso | Prueba |
|---|---|
| Resolver la carrera de duplicados de creación (Backend). | AP-11, PE-03 |
| `userId` nunca del body. | AP-09 |
| Sin trazas ni SQL en errores. | AP-05, AP-07, AP-08, AP-13, UI-06 |
| `X-Correlation-Id` saneado. | AP-10 |
| Reutilizar clave en reintento y clave nueva al reiniciar (Frontend). | UI-07a, UI-07b |
| Paso actual desde `steps[].status` (SP-5). | UI-01b |
| Sin navegación a `target` del servidor. | UI-08 |
| Sin `innerHTML`. | UI-11 |
| ARIA y foco. | UI-01, UI-02, UI-12 (contraste y lector de pantalla: BL-08) |

---

## 6. Preparación del entorno y datos

### 6.1 Requisitos pendientes (no asumidos como instalados)

| Requisito | Estado |
|---|---|
| JDK compatible con el proyecto (el código usa `jakarta.*`, records y `java.net.http`; mínimo Java 17) | **Pendiente de confirmar** |
| Herramienta de build (Maven asumido en los comandos; si es Gradle, usar la tarea `test` equivalente con `-D` hacia el JVM de pruebas) | **Pendiente: no se vio `pom.xml`** |
| Dependencias de test: `junit-jupiter` (api, params, engine), `jackson-databind`, `jakarta.validation-api`, `hibernate-validator`, driver `org.postgresql:postgresql`. Versiones según el BOM del proyecto. | **Pendiente de confirmar/añadir** |
| Servicio de onboarding en ejecución contra una **BD de pruebas** con el esquema aplicado (migración de Datos no recibida) | **Pendiente** |
| Dos usuarios de prueba (A y B) con token válido y su `userId` (mecanismo de emisión desconocido, H-07) | **Pendiente** |
| Node.js, `@playwright/test` y navegador Chromium | **Pendiente** |
| Aplicación Angular servida (p. ej. `ng serve`) y ruta de la pantalla de onboarding | **Pendiente: ruta desconocida** |

Fragmento de dependencias de prueba (sin versiones, para completar con el BOM del proyecto):

```xml
<!-- pom.xml (scope test) — PENDIENTE de confirmar contra el proyecto real -->
<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><scope>test</scope></dependency>
<dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
```

### 6.2 Propiedades de prueba

| Propiedad (`-D`) | Uso |
|---|---|
| `onboarding.baseUrl` | URL base del servicio, p. ej. `http://localhost:8080` |
| `onboarding.token.a` / `onboarding.token.b` | Tokens Bearer de los usuarios A y B |
| `onboarding.user.a.id` / `onboarding.user.b.id` | UUID de esos usuarios (el `userId` real del token) |
| `onboarding.db.url`, `onboarding.db.user`, `onboarding.db.password` | Conexión JDBC a la BD de pruebas |
| `onboarding.db.allowDestructive=true` | Autoriza el borrado de filas de A y B |

Sin las propiedades necesarias, las pruebas se **omiten** con `Assumptions`. Una prueba omitida no es una prueba pasada.

### 6.3 Datos de prueba

- Usuarios A y B sin sesiones. Las pruebas limpian sus filas antes y después, solo por `user_id` de A y B.
- Canales: `web-test`, cadena de 50 y de 51 caracteres.
- Claves de idempotencia: UUID v4 generados en cada prueba.
- **Nunca** apuntar la BD a producción ni a datos de usuarios reales.

---

## 7. Comandos reproducibles

Convención: los secretos se pasan por variables de entorno del shell, no se escriben en el historial.

```bash
export ONB_BASE_URL="http://localhost:8080"
export ONB_TOKEN_A="<token_A>"  ; export ONB_USER_A="<uuid_A>"
export ONB_TOKEN_B="<token_B>"  ; export ONB_USER_B="<uuid_B>"
export ONB_DB_URL="jdbc:postgresql://localhost:5432/<bd_de_pruebas>"
export ONB_DB_USER="<usuario>"  ; export ONB_DB_PASS="<clave>"
```

**Grupo 1 — Unitarias (sin servicio ni BD).** Requieren las dependencias del §6.1.

```bash
mvn test -Dtest='StartOnboardingRequestValidationTest,OnboardingExceptionsTest'
```

**Grupo 2 — API E1 (caja negra).** Requiere servicio levantado.

```bash
mvn test -Dtest='OnboardingSessionsApiTest' \
  -Donboarding.baseUrl="$ONB_BASE_URL" \
  -Donboarding.token.a="$ONB_TOKEN_A" -Donboarding.user.a.id="$ONB_USER_A" \
  -Donboarding.token.b="$ONB_TOKEN_B" -Donboarding.user.b.id="$ONB_USER_B" \
  -Donboarding.db.url="$ONB_DB_URL" -Donboarding.db.user="$ONB_DB_USER" \
  -Donboarding.db.password="$ONB_DB_PASS" -Donboarding.db.allowDestructive=true
```

**Grupo 3 — Persistencia.** Mismas propiedades que el grupo 2.

```bash
mvn test -Dtest='OnboardingSessionPersistenceTest' \
  -Donboarding.baseUrl="$ONB_BASE_URL" \
  -Donboarding.token.a="$ONB_TOKEN_A" -Donboarding.user.a.id="$ONB_USER_A" \
  -Donboarding.token.b="$ONB_TOKEN_B" -Donboarding.user.b.id="$ONB_USER_B" \
  -Donboarding.db.url="$ONB_DB_URL" -Donboarding.db.user="$ONB_DB_USER" \
  -Donboarding.db.password="$ONB_DB_PASS" -Donboarding.db.allowDestructive=true
```

**Grupo 4 — UI (Playwright).**

```bash
npm i -D @playwright/test
npx playwright install chromium
# En otra terminal: servir la app Angular (comando del proyecto, p. ej. npx ng serve)
export ONBOARDING_URL="http://localhost:4200/<ruta-onboarding>"
npx playwright test e2e/onboarding.spec.ts
# Solo si la app NO define campos de paso (token ONBOARDING_STEP_FIELDS vacío):
STEP_FIELDS_EMPTY=true npx playwright test e2e/onboarding.spec.ts -g "UI-03"
```

**Registro de resultados reales:** al ejecutar, guardar la salida en `target/surefire-reports/` y `playwright-report/`. Este informe no incluye esas salidas porque no existen.

---

## 8. Limpieza

1. Las pruebas Java borran en `@BeforeEach`/`@AfterEach` las filas de `onboarding_session` de los usuarios A y B (solo con `allowDestructive=true`).
2. Limpieza manual si una ejecución se interrumpe (revisar antes si existen tablas hijas o de eventos dependientes, no conocidas):
   ```sql
   DELETE FROM onboarding_session WHERE user_id IN ('<uuid_A>', '<uuid_B>');
   ```
3. UI: `rm -rf playwright-report test-results`.
4. Revocar o dejar expirar los tokens de prueba y desactivar `ONB_*` (`unset ONB_TOKEN_A ONB_TOKEN_B ONB_DB_PASS`).

---

## 9. Código de las pruebas

Estructura propuesta (rutas relativas al módulo del backend y al proyecto Angular):

```
src/test/java/pe/bcp/login/support/TestConfig.java
src/test/java/pe/bcp/login/support/OnboardingApiClient.java
src/test/java/pe/bcp/login/support/TestDb.java
src/test/java/pe/bcp/login/dto/StartOnboardingRequestValidationTest.java
src/test/java/pe/bcp/login/exception/OnboardingExceptionsTest.java
src/test/java/pe/bcp/login/resource/OnboardingSessionsApiTest.java
src/test/java/pe/bcp/login/repository/OnboardingSessionPersistenceTest.java
e2e/onboarding.spec.ts
playwright.config.ts
```

### 9.1 Soporte

```java
// src/test/java/pe/bcp/login/support/TestConfig.java
package pe.bcp.login.support;

import java.util.UUID;

public final class TestConfig {

    private TestConfig() {
    }

    public static String get(String key) {
        String v = System.getProperty(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public static String baseUrl() {
        String v = get("onboarding.baseUrl");
        return v == null ? null : v.replaceAll("/+$", "");
    }

    public static String tokenA() {
        return get("onboarding.token.a");
    }

    public static String tokenB() {
        return get("onboarding.token.b");
    }

    public static UUID userAId() {
        String v = get("onboarding.user.a.id");
        return v == null ? null : UUID.fromString(v);
    }

    public static UUID userBId() {
        String v = get("onboarding.user.b.id");
        return v == null ? null : UUID.fromString(v);
    }

    /** BD de pruebas configurada, autorizada para borrado y con ambos usuarios definidos. */
    public static boolean dbReady() {
        return get("onboarding.db.url") != null
                && get("onboarding.db.user") != null
                && "true".equalsIgnoreCase(get("onboarding.db.allowDestructive"))
                && userAId() != null
                && userBId() != null;
    }
}
```

```java
// src/test/java/pe/bcp/login/support/OnboardingApiClient.java
package pe.bcp.login.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cliente HTTP de caja negra para E1. Solo usa el JDK y Jackson. */
public final class OnboardingApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Reply(int status, String body, String contentType) {
        public JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                throw new IllegalStateException("El cuerpo no es JSON: " + body, e);
            }
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;

    public OnboardingApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Cualquier parámetro null omite la cabecera o el body correspondiente. */
    public Reply postSessions(String bearerToken, String idempotencyKey,
                              String correlationId, String jsonBody) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/onboarding/sessions"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if (bearerToken != null) {
                b.header("Authorization", "Bearer " + bearerToken);
            }
            if (idempotencyKey != null) {
                b.header("Idempotency-Key", idempotencyKey);
            }
            if (correlationId != null) {
                b.header("X-Correlation-Id", correlationId);
            }
            b.POST(jsonBody == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.body(),
                    r.headers().firstValue("content-type").orElse(""));
        } catch (IOException e) {
            throw new IllegalStateException("Fallo de red al llamar a E1", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Llamada interrumpida", e);
        }
    }
}
```

```java
// src/test/java/pe/bcp/login/support/TestDb.java
package pe.bcp.login.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Acceso JDBC a la BD de PRUEBAS.
 * SUPUESTO (sin confirmar por Datos): tabla onboarding_session con columnas
 * id, user_id, status, current_step_index, version, wallet_status,
 * acquisition_channel, started_at, updated_at.
 */
public final class TestDb implements AutoCloseable {

    private final Connection conn;

    private TestDb(Connection conn) {
        this.conn = conn;
    }

    public static TestDb open() throws SQLException {
        return new TestDb(DriverManager.getConnection(
                TestConfig.get("onboarding.db.url"),
                TestConfig.get("onboarding.db.user"),
                TestConfig.get("onboarding.db.password")));
    }

    public void deleteSessionsOf(UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM onboarding_session WHERE user_id = ?")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
    }

    /** status null = cualquier estado. */
    public int count(UUID userId, String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM onboarding_session WHERE user_id = ?"
                + (status == null ? "" : " AND status::text = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            if (status != null) {
                ps.setString(2, status);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public Map<String, Object> row(UUID sessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM onboarding_session WHERE id = ?")) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (rs.next()) {
                    ResultSetMetaData md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        m.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                    }
                }
                return m;
            }
        }
    }

    public void markCompleted(UUID sessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE onboarding_session SET status = 'COMPLETED' WHERE id = ?")) {
            ps.setObject(1, sessionId);
            ps.executeUpdate();
        }
    }

    /** Inserta una copia IN_PROGRESS de una fila existente (mismo user_id) para forzar la restricción. */
    public void copyAsInProgress(UUID sourceId, UUID newId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO onboarding_session (id, user_id, status, current_step_index, version, "
                        + "wallet_status, acquisition_channel, started_at, updated_at) "
                        + "SELECT ?, user_id, 'IN_PROGRESS', current_step_index, version, "
                        + "wallet_status, acquisition_channel, started_at, updated_at "
                        + "FROM onboarding_session WHERE id = ?")) {
            ps.setObject(1, newId);
            ps.setObject(2, sourceId);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
```

### 9.2 Unitarias

```java
// src/test/java/pe/bcp/login/dto/StartOnboardingRequestValidationTest.java
package pe.bcp.login.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class StartOnboardingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static Set<ConstraintViolation<StartOnboardingRequest>> validar(String canal) {
        StartOnboardingRequest r = new StartOnboardingRequest();
        r.acquisitionChannel = canal;
        return validator.validate(r);
    }

    @Test
    @DisplayName("UT-01 canal null es válido (opcional)")
    void ut01() {
        assertTrue(validar(null).isEmpty());
    }

    @Test
    @DisplayName("UT-02 canal de 50 caracteres es válido (borde superior)")
    void ut02() {
        assertTrue(validar("a".repeat(50)).isEmpty());
    }

    @Test
    @DisplayName("UT-03 canal de 51 caracteres es inválido con mensaje accionable")
    void ut03() {
        Set<ConstraintViolation<StartOnboardingRequest>> v = validar("a".repeat(51));
        assertEquals(1, v.size());
        ConstraintViolation<StartOnboardingRequest> c = v.iterator().next();
        assertEquals("acquisitionChannel", c.getPropertyPath().toString());
        assertEquals("El canal de adquisición no puede superar 50 caracteres.", c.getMessage());
    }

    @Test
    @DisplayName("UT-04 canal vacío es válido para la anotación @Size")
    void ut04() {
        assertTrue(validar("").isEmpty());
    }
}
```

```java
// src/test/java/pe/bcp/login/exception/OnboardingExceptionsTest.java
package pe.bcp.login.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnboardingExceptionsTest {

    @Test
    @DisplayName("UT-05 OnboardingValidationException copia defensivamente y conserva el orden")
    void ut05() {
        Map<String, String> origen = new LinkedHashMap<>();
        origen.put("b", "2");
        origen.put("a", "1");

        OnboardingValidationException ex = new OnboardingValidationException("msg", origen);
        origen.put("c", "3");

        assertEquals("msg", ex.getMessage());
        assertEquals(List.of("b", "a"), List.copyOf(ex.getFieldErrors().keySet()));
        assertEquals("2", ex.getFieldErrors().get("b"));
    }

    @Test
    @DisplayName("UT-06 StorageException conserva la causa")
    void ut06() {
        RuntimeException causa = new RuntimeException("x");
        StorageException ex = new StorageException("m", causa);
        assertEquals("m", ex.getMessage());
        assertSame(causa, ex.getCause());
    }

    @Test
    @DisplayName("UT-07 DuplicateSessionException conserva el mensaje")
    void ut07() {
        DuplicateSessionException ex = new DuplicateSessionException("dup");
        assertEquals("dup", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }
}
```

### 9.3 API E1

```java
// src/test/java/pe/bcp/login/resource/OnboardingSessionsApiTest.java
package pe.bcp.login.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static pe.bcp.login.support.OnboardingApiClient.newKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;

import pe.bcp.login.support.OnboardingApiClient;
import pe.bcp.login.support.OnboardingApiClient.Reply;
import pe.bcp.login.support.TestConfig;
import pe.bcp.login.support.TestDb;

class OnboardingSessionsApiTest {

    private static OnboardingApiClient api;

    @BeforeAll
    static void conectar() {
        assumeTrue(TestConfig.baseUrl() != null, "Falta -Donboarding.baseUrl");
        api = new OnboardingApiClient(TestConfig.baseUrl());
    }

    @BeforeEach
    void limpiarAntes() {
        limpiarUsuarios();
    }

    @AfterEach
    void limpiarDespues() {
        limpiarUsuarios();
    }

    private static void limpiarUsuarios() {
        if (!TestConfig.dbReady()) {
            return;
        }
        try (TestDb db = TestDb.open()) {
            db.deleteSessionsOf(TestConfig.userAId());
            db.deleteSessionsOf(TestConfig.userBId());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo limpiar la BD de pruebas", e);
        }
    }

    /** Pruebas que necesitan estado inicial limpio y tokens de ambos usuarios. */
    private static void requireFresh() {
        assumeTrue(TestConfig.dbReady(), "Falta configuración de BD de pruebas (onboarding.db.*)");
        assumeTrue(TestConfig.tokenA() != null && TestConfig.tokenB() != null,
                "Faltan onboarding.token.a / onboarding.token.b");
    }

    private static void assertNoLeak(Reply r) {
        String b = r.body() == null ? "" : r.body();
        for (String f : List.of("\tat ", "pe.bcp.", "org.postgresql", "SQLException",
                "SELECT ", "INSERT ", "org.hibernate", "java.lang.")) {
            assertFalse(b.contains(f), "Fuga de detalle interno: '" + f + "' en " + b);
        }
    }

    private static void assertModeloProgreso(JsonNode j) {
        UUID.fromString(j.get("sessionId").asText());
        assertTrue(j.get("steps").isArray() && j.get("steps").size() > 0, "steps vacío");
        assertEquals(j.get("steps").size(), j.get("totalSteps").asInt());
        assertTrue(j.get("version").isInt(), "version debe ser entero");
        assertTrue(Set.of("PENDING", "ACTIVE").contains(j.get("walletStatus").asText()));
        assertTrue(j.hasNonNull("nextAction") && j.get("nextAction").hasNonNull("type"));

        int previo = Integer.MIN_VALUE;
        int current = 0;
        String currentId = null;
        for (JsonNode s : j.get("steps")) {
            assertTrue(s.hasNonNull("stepId") && s.hasNonNull("label")
                    && s.has("required") && s.has("order"));
            assertTrue(s.get("order").asInt() > previo, "order debe ser creciente");
            previo = s.get("order").asInt();
            String st = s.get("status").asText();
            assertTrue(Set.of("COMPLETED", "CURRENT", "PENDING").contains(st), "status de paso: " + st);
            if ("CURRENT".equals(st)) {
                current++;
                currentId = s.get("stepId").asText();
            }
        }
        assertEquals(1, current, "Debe haber exactamente un paso CURRENT");
        assertEquals(currentId, j.get("currentStepId").asText());
    }

    // ---------------------------------------------------------------- positivos

    @Test
    @DisplayName("AP-01 crea sesión nueva: 201 y modelo de progreso")
    void ap01() {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(201, r.status(), r.body());
        assertTrue(r.contentType().contains("application/json"));
        JsonNode j = r.json();
        assertModeloProgreso(j);
        assertEquals("IN_PROGRESS", j.get("status").asText());
        assertEquals("PENDING", j.get("walletStatus").asText());
        assertEquals("CONTINUE_STEP", j.get("nextAction").get("type").asText());
        JsonNode steps = j.get("steps");
        assertEquals("CURRENT", steps.get(0).get("status").asText());
        for (int i = 1; i < steps.size(); i++) {
            assertEquals("PENDING", steps.get(i).get("status").asText());
        }
    }

    @Test
    @DisplayName("AP-02 reanuda con otra clave: 200 y mismo sessionId")
    void ap02() {
        requireFresh();
        Reply primera = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(201, primera.status(), primera.body());
        Reply segunda = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(200, segunda.status(), segunda.body());
        assertEquals(primera.json().get("sessionId").asText(), segunda.json().get("sessionId").asText());
        assertModeloProgreso(segunda.json());
    }

    @Test
    @DisplayName("AP-03 misma clave repetida: mismo sessionId (status 200 o 201)")
    void ap03() {
        requireFresh();
        String clave = newKey();
        Reply a = api.postSessions(TestConfig.tokenA(), clave, null, null);
        Reply b = api.postSessions(TestConfig.tokenA(), clave, null, null);
        assertTrue(Set.of(200, 201).contains(a.status()), a.body());
        assertTrue(Set.of(200, 201).contains(b.status()), b.body());
        assertEquals(a.json().get("sessionId").asText(), b.json().get("sessionId").asText());
    }

    @Test
    @Tag("catalogo-provisional")
    @DisplayName("AP-12 catálogo provisional de 3 pasos requeridos (SUPUESTO sin confirmar)")
    void ap12() {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(201, r.status(), r.body());
        JsonNode steps = r.json().get("steps");
        List<String> ids = new ArrayList<>();
        for (JsonNode s : steps) {
            ids.add(s.get("stepId").asText());
            assertTrue(s.get("required").asBoolean());
        }
        assertEquals(List.of("PERSONAL_DATA", "IDENTITY_DOCUMENT", "CONFIRMATION"), ids);
        assertEquals(3, r.json().get("totalSteps").asInt());
    }

    // -------------------------------------------------------------------- bordes

    @Test
    @DisplayName("AP-04 canal de 50 caracteres: 201")
    void ap04() {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null,
                "{\"acquisitionChannel\":\"" + "a".repeat(50) + "\"}");
        assertEquals(201, r.status(), r.body());
    }

    @Test
    @DisplayName("AP-14 body {} equivale a body vacío: 201")
    void ap14() {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, "{}");
        assertEquals(201, r.status(), r.body());
    }

    // ----------------------------------------------------------------- negativos

    @Test
    @DisplayName("AP-05 canal de 51 caracteres: 400 con fieldErrors y sin efectos")
    void ap05() throws Exception {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null,
                "{\"acquisitionChannel\":\"" + "a".repeat(51) + "\"}");
        assertEquals(400, r.status(), r.body());
        JsonNode j = r.json();
        assertTrue(j.hasNonNull("code") && !j.get("code").asText().isBlank());
        assertTrue(j.hasNonNull("message") && !j.get("message").asText().isBlank());
        assertTrue(j.hasNonNull("correlationId") && !j.get("correlationId").asText().isBlank());
        assertTrue(j.get("fieldErrors").isArray() && j.get("fieldErrors").size() >= 1);
        boolean campo = false;
        for (JsonNode fe : j.get("fieldErrors")) {
            campo |= fe.get("field").asText().contains("acquisitionChannel");
        }
        assertTrue(campo, "fieldErrors debe referir a acquisitionChannel: " + r.body());
        assertNoLeak(r);
        try (TestDb db = TestDb.open()) {
            assertEquals(0, db.count(TestConfig.userAId(), null));
        }
    }

    @Test
    @DisplayName("AP-06 sin Idempotency-Key: 400 y sin efectos")
    void ap06() throws Exception {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), null, null, null);
        assertEquals(400, r.status(), r.body());
        assertNoLeak(r);
        try (TestDb db = TestDb.open()) {
            assertEquals(0, db.count(TestConfig.userAId(), null));
        }
    }

    @ParameterizedTest(name = "AP-07 clave inválida [{0}]")
    @ValueSource(strings = {"no-es-uuid", "123", "00000000-0000-0000-0000-00000000000", "' OR 1=1 --"})
    void ap07(String claveInvalida) {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), claveInvalida, null, null);
        assertEquals(400, r.status(), r.body());
        assertNoLeak(r);
    }

    @Test
    @DisplayName("AP-08a sin Authorization: 401")
    void ap08a() {
        Reply r = api.postSessions(null, newKey(), null, null);
        assertEquals(401, r.status(), r.body());
        assertNoLeak(r);
    }

    @Test
    @DisplayName("AP-08b token inválido: 401")
    void ap08b() {
        Reply r = api.postSessions("token.invalido.xyz", newKey(), null, null);
        assertEquals(401, r.status(), r.body());
        assertNoLeak(r);
    }

    @Test
    @DisplayName("AP-13 JSON malformado: 400 y sin fuga")
    void ap13() {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, "{\"acquisitionChannel\":");
        assertEquals(400, r.status(), r.body());
        assertNoLeak(r);
    }

    // ------------------------------------------------------------------ seguridad

    @Test
    @DisplayName("AP-09 el userId del body se ignora: la sesión pertenece al token")
    void ap09() throws Exception {
        requireFresh();
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null,
                "{\"userId\":\"" + TestConfig.userBId() + "\"}");
        assertTrue(Set.of(200, 201, 400).contains(r.status()), r.body());
        if (r.status() != 400) {
            try (TestDb db = TestDb.open()) {
                assertEquals(1, db.count(TestConfig.userAId(), null));
                assertEquals(0, db.count(TestConfig.userBId(), null));
                Map<String, Object> fila = db.row(UUID.fromString(r.json().get("sessionId").asText()));
                assertEquals(TestConfig.userAId().toString(), String.valueOf(fila.get("user_id")));
            }
        }
    }

    @Test
    @DisplayName("AP-10 X-Correlation-Id: se genera, se conserva (UUID) y se sanea")
    void ap10() {
        requireFresh();
        // Se provoca un 400 estable (falta Idempotency-Key) para observar correlationId.
        Reply sin = api.postSessions(TestConfig.tokenA(), null, null, null);
        assertEquals(400, sin.status(), sin.body());
        assertFalse(sin.json().get("correlationId").asText().isBlank());

        String propio = UUID.randomUUID().toString();
        Reply con = api.postSessions(TestConfig.tokenA(), null, propio, null);
        assertEquals(400, con.status(), con.body());
        assertEquals(propio, con.json().get("correlationId").asText()); // inferencia: UUID se considera válido

        Reply hostil = api.postSessions(TestConfig.tokenA(), null, "<script>alert(1)</script>", null);
        assertEquals(400, hostil.status(), hostil.body());
        assertFalse(hostil.json().get("correlationId").asText().contains("<script>"));
    }

    // --------------------------------------------------------------- concurrencia

    @Test
    @DisplayName("AP-11 8 POST simultáneos del mismo usuario: una sola sesión creada")
    void ap11() throws Exception {
        requireFresh();
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch listos = new CountDownLatch(n);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Reply>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                futuros.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    return api.postSessions(TestConfig.tokenB(), newKey(), null, null);
                }));
            }
            listos.await();
            salida.countDown();

            int creadas = 0;
            Set<String> ids = new HashSet<>();
            for (Future<Reply> f : futuros) {
                Reply r = f.get(30, TimeUnit.SECONDS);
                assertTrue(Set.of(200, 201).contains(r.status()), r.status() + " " + r.body());
                if (r.status() == 201) {
                    creadas++;
                }
                ids.add(r.json().get("sessionId").asText());
            }
            assertEquals(1, creadas, "Exactamente una respuesta 201");
            assertEquals(1, ids.size(), "Un único sessionId");
            try (TestDb db = TestDb.open()) {
                assertEquals(1, db.count(TestConfig.userBId(), null));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
```

### 9.4 Persistencia

```java
// src/test/java/pe/bcp/login/repository/OnboardingSessionPersistenceTest.java
package pe.bcp.login.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static pe.bcp.login.support.OnboardingApiClient.newKey;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.bcp.login.support.OnboardingApiClient;
import pe.bcp.login.support.OnboardingApiClient.Reply;
import pe.bcp.login.support.TestConfig;
import pe.bcp.login.support.TestDb;

/**
 * Verifica persistencia contra el ESQUEMA SUPUESTO por Backend (no confirmado por Datos):
 * tabla onboarding_session e índice único parcial (user_id) WHERE status='IN_PROGRESS'.
 */
class OnboardingSessionPersistenceTest {

    private static OnboardingApiClient api;

    @BeforeAll
    static void preparar() {
        assumeTrue(TestConfig.baseUrl() != null, "Falta -Donboarding.baseUrl");
        assumeTrue(TestConfig.dbReady(), "Falta configuración de BD de pruebas (onboarding.db.*)");
        assumeTrue(TestConfig.tokenA() != null && TestConfig.tokenB() != null,
                "Faltan tokens de A y B");
        api = new OnboardingApiClient(TestConfig.baseUrl());
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        // JUnit 5 permite anotar el mismo método con ambos ciclos de vida.
        try (TestDb db = TestDb.open()) {
            db.deleteSessionsOf(TestConfig.userAId());
            db.deleteSessionsOf(TestConfig.userBId());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo limpiar la BD de pruebas", e);
        }
    }

    private static UUID crearSesionA(String bodyJson) {
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, bodyJson);
        assertEquals(201, r.status(), r.body());
        return UUID.fromString(r.json().get("sessionId").asText());
    }

    @Test
    @DisplayName("PE-01 la fila creada refleja usuario, estado, wallet y canal")
    void pe01() throws Exception {
        UUID id = crearSesionA("{\"acquisitionChannel\":\"web-test\"}");
        try (TestDb db = TestDb.open()) {
            Map<String, Object> f = db.row(id);
            assertEquals(id.toString(), String.valueOf(f.get("id")));
            assertEquals(TestConfig.userAId().toString(), String.valueOf(f.get("user_id")));
            assertEquals("IN_PROGRESS", String.valueOf(f.get("status")));
            assertEquals("PENDING", String.valueOf(f.get("wallet_status")));
            assertEquals("web-test", String.valueOf(f.get("acquisition_channel")));
            assertNotNull(f.get("started_at"));
            assertNotNull(f.get("updated_at"));
        }
    }

    @Test
    @DisplayName("PE-02 reanudar no crea filas adicionales")
    void pe02() throws Exception {
        crearSesionA(null);
        Reply segunda = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(200, segunda.status(), segunda.body());
        try (TestDb db = TestDb.open()) {
            assertEquals(1, db.count(TestConfig.userAId(), null));
        }
    }

    @Test
    @DisplayName("PE-03 el índice único parcial rechaza una segunda IN_PROGRESS (23505)")
    void pe03() throws Exception {
        UUID id = crearSesionA(null);
        try (TestDb db = TestDb.open()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> db.copyAsInProgress(id, UUID.randomUUID()));
            assertEquals("23505", ex.getSQLState());
            assertEquals(1, db.count(TestConfig.userAId(), "IN_PROGRESS"));
        }
    }

    @Test
    @DisplayName("PE-04 tras COMPLETED se permite una nueva IN_PROGRESS")
    void pe04() throws Exception {
        UUID id = crearSesionA(null);
        try (TestDb db = TestDb.open()) {
            db.markCompleted(id);
            db.copyAsInProgress(id, UUID.randomUUID());
            assertEquals(2, db.count(TestConfig.userAId(), null));
            assertEquals(1, db.count(TestConfig.userAId(), "IN_PROGRESS"));
        }
    }

    @Test
    @DisplayName("PE-05 cada usuario tiene su propia IN_PROGRESS")
    void pe05() throws Exception {
        UUID a = crearSesionA(null);
        Reply rb = api.postSessions(TestConfig.tokenB(), newKey(), null, null);
        assertEquals(201, rb.status(), rb.body());
        UUID b = UUID.fromString(rb.json().get("sessionId").asText());
        assertNotEquals(a, b);
        try (TestDb db = TestDb.open()) {
            assertEquals(1, db.count(TestConfig.userAId(), "IN_PROGRESS"));
            assertEquals(1, db.count(TestConfig.userBId(), "IN_PROGRESS"));
        }
    }

    @Test
    @DisplayName("PE-06 tras COMPLETED, E1 crea una sesión nueva (201)")
    void pe06() throws Exception {
        UUID primera = crearSesionA(null);
        try (TestDb db = TestDb.open()) {
            db.markCompleted(primera);
        }
        Reply r = api.postSessions(TestConfig.tokenA(), newKey(), null, null);
        assertEquals(201, r.status(), r.body());
        assertNotEquals(primera.toString(), r.json().get("sessionId").asText());
    }
}
```

### 9.5 UI (Playwright)

```ts
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: { headless: true },
  reporter: [['list'], ['html', { open: 'never' }]],
});
```

```ts
// e2e/onboarding.spec.ts
import { test, expect, Page, Route } from '@playwright/test';

const URL_ONBOARDING = process.env.ONBOARDING_URL ?? '';
const STEP_FIELDS_EMPTY = process.env.STEP_FIELDS_EMPTY === 'true';
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

test.skip(!URL_ONBOARDING, 'Defina ONBOARDING_URL con la ruta de la pantalla de onboarding');

const STEP_DEFS = [
  { stepId: 'PERSONAL_DATA', order: 1, label: 'Datos personales', required: true },
  { stepId: 'IDENTITY_DOCUMENT', order: 2, label: 'Documento de identidad', required: true },
  { stepId: 'CONFIRMATION', order: 3, label: 'Confirmación', required: true },
];

function progress(currentOrder: number, extra: Record<string, unknown> = {}) {
  const steps = STEP_DEFS.map((s) => ({
    ...s,
    status: s.order < currentOrder ? 'COMPLETED' : s.order === currentOrder ? 'CURRENT' : 'PENDING',
  }));
  const cur = steps.find((s) => s.status === 'CURRENT');
  return {
    sessionId: '11111111-1111-4111-8111-111111111111',
    status: 'IN_PROGRESS',
    totalSteps: steps.length,
    currentStepIndex: currentOrder, // ambiguo (H-03): la UI no debe depender de él
    currentStepId: cur?.stepId,
    steps,
    version: 1,
    walletStatus: 'PENDING',
    nextAction: { type: 'CONTINUE_STEP', target: cur?.stepId },
    ...extra,
  };
}

function allCompleted(extra: Record<string, unknown> = {}) {
  const steps = STEP_DEFS.map((s) => ({ ...s, status: 'COMPLETED' }));
  return progress(1, {
    status: 'COMPLETED',
    walletStatus: 'ACTIVE',
    steps,
    currentStepId: 'CONFIRMATION',
    nextAction: { type: 'GO_TO_LOW_AMOUNT_PAYMENTS', target: 'LOW_AMOUNT_PAYMENTS_ENTRY' },
    ...extra,
  });
}

async function json(route: Route, status: number, body: unknown) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function mockStart(page: Page, handler: (route: Route, n: number) => Promise<void>) {
  let n = 0;
  await page.route('**/onboarding/sessions', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }
    n += 1;
    await handler(route, n);
  });
}

test('UI-01 indicador de pasos y paso actual (paso 2 de 3)', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(2)));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Paso 2 de 3')).toBeVisible();
  await expect(page.locator('nav ol li')).toHaveCount(3);
  await expect(page.locator('[aria-current="step"]')).toHaveCount(1);
  await expect(page.locator('[aria-current="step"]')).toContainText('Documento de identidad');
});

test('UI-01b el paso actual sale de steps[].status, no de currentStepIndex', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(2, { currentStepIndex: 99 })));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Paso 2 de 3')).toBeVisible();
});

test('UI-02 estados del indicador no dependen solo del color', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(2)));
  await page.goto(URL_ONBOARDING);
  const nav = page.locator('nav ol');
  await expect(nav).toContainText('completado');
  await expect(nav).toContainText('paso actual');
  await expect(nav).toContainText('pendiente');
});

test('UI-03 paso sin definición de campos: Continuar deshabilitado', async ({ page }) => {
  test.skip(!STEP_FIELDS_EMPTY, 'Aplica solo si ONBOARDING_STEP_FIELDS está vacío (STEP_FIELDS_EMPTY=true)');
  await mockStart(page, (r) => json(r, 200, progress(1)));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Este paso todavía no está disponible')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Continuar' })).toBeDisabled();
});

test('UI-04 estado de carga visible mientras responde E1', async ({ page }) => {
  await mockStart(page, async (r) => {
    await new Promise((res) => setTimeout(res, 1500));
    await json(r, 200, progress(1));
  });
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Cargando tu registro')).toBeVisible();
  await expect(page.getByText('Paso 1 de 3')).toBeVisible();
  await expect(page.getByText('Cargando tu registro')).toHaveCount(0);
});

test('UI-05 estado vacío cuando no hay pasos', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(1, { steps: [], totalSteps: 0, currentStepId: null })));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('No hay pasos disponibles por ahora')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
});

const ERRORES: { http: number | 'abort'; titulo: string; boton?: string }[] = [
  { http: 'abort', titulo: 'Sin conexión', boton: 'Reintentar' },
  { http: 400, titulo: 'No pudimos iniciar tu registro' },
  { http: 401, titulo: 'Tu sesión venció', boton: 'Iniciar sesión' },
  { http: 403, titulo: 'Sin permiso' },
  { http: 404, titulo: 'Registro no encontrado', boton: 'Reintentar' },
  { http: 409, titulo: 'Tu registro cambió' },
  { http: 500, titulo: 'Problema temporal', boton: 'Reintentar' },
  { http: 503, titulo: 'Problema temporal', boton: 'Reintentar' },
];

for (const c of ERRORES) {
  test(`UI-06 error ${c.http}: mensaje mapeado, sin fuga de detalles`, async ({ page }) => {
    await mockStart(page, async (r) => {
      if (c.http === 'abort') {
        await r.abort('failed');
        return;
      }
      await json(r, c.http, {
        code: 'X',
        message: 'org.postgresql.util.PSQLException at pe.bcp.login.repository',
        correlationId: 'abc-123',
      });
    });
    await page.goto(URL_ONBOARDING);
    await expect(page.getByRole('alert')).toContainText(c.titulo);
    if (c.boton) {
      await expect(page.getByRole('button', { name: c.boton })).toBeVisible();
    }
    await expect(page.locator('body')).not.toContainText('PSQLException');
    await expect(page.locator('body')).not.toContainText('pe.bcp');
  });
}

test('UI-07a el reintento tras 5xx reutiliza la misma Idempotency-Key', async ({ page }) => {
  const claves: (string | undefined)[] = [];
  await mockStart(page, async (route, n) => {
    claves.push(route.request().headers()['idempotency-key']);
    if (n === 1) {
      await json(route, 503, { code: 'X' });
      return;
    }
    await json(route, 200, progress(1));
  });
  await page.goto(URL_ONBOARDING);
  await expect(page.getByRole('alert')).toContainText('Problema temporal');
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(page.getByText('Paso 1 de 3')).toBeVisible();
  expect(claves).toHaveLength(2);
  expect(claves[0]).toMatch(UUID_RE);
  expect(claves[1]).toBe(claves[0]);
});

test('UI-07b "Empezar de nuevo" usa una Idempotency-Key distinta', async ({ page }) => {
  const claves: (string | undefined)[] = [];
  await mockStart(page, async (route, n) => {
    claves.push(route.request().headers()['idempotency-key']);
    if (n === 1) {
      await json(route, 200, progress(1, { status: 'CLOSED_INCOMPLETE' }));
      return;
    }
    await json(route, 201, progress(1));
  });
  await page.goto(URL_ONBOARDING);
  await page.getByRole('button', { name: 'Empezar de nuevo' }).click();
  await expect(page.getByText('Paso 1 de 3')).toBeVisible();
  expect(claves).toHaveLength(2);
  expect(claves[1]).toMatch(UUID_RE);
  expect(claves[1]).not.toBe(claves[0]);
});

test('UI-08 COMPLETED + ACTIVE muestra el acceso y no navega a un target externo', async ({ page }) => {
  const peticiones: string[] = [];
  page.on('request', (req) => peticiones.push(req.url()));
  await page.route('**://evil.example/**', (r) => r.abort());
  await mockStart(page, (r) =>
    json(r, 200, allCompleted({
      nextAction: { type: 'GO_TO_LOW_AMOUNT_PAYMENTS', target: 'https://evil.example/robo' },
    })));
  await page.goto(URL_ONBOARDING);
  const boton = page.getByRole('button', { name: 'Ir a pagos de bajo monto' });
  await expect(boton).toBeVisible();
  await boton.click();
  await page.waitForLoadState('networkidle');
  expect(new URL(page.url()).hostname).not.toBe('evil.example');
  expect(peticiones.some((u) => u.includes('evil.example'))).toBe(false);
});

test('UI-09 COMPLETED con wallet PENDING no ofrece el acceso a pagos', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, allCompleted({ walletStatus: 'PENDING' })));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByRole('button', { name: 'Ir a pagos de bajo monto' })).toHaveCount(0);
});

test('UI-10 CLOSED_INCOMPLETE ofrece Empezar de nuevo', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(1, { status: 'CLOSED_INCOMPLETE' })));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByRole('button', { name: 'Empezar de nuevo' })).toBeVisible();
});

test('UI-11 una etiqueta con HTML no se ejecuta', async ({ page }) => {
  const payload = progress(1);
  payload.steps[0].label = '<img src=x onerror="window.__xss=1">Datos';
  await mockStart(page, (r) => json(r, 200, payload));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Paso 1 de 3')).toBeVisible();
  const xss = await page.evaluate(() => (window as unknown as { __xss?: number }).__xss);
  expect(xss).toBeUndefined();
});

test('UI-12 tras cargar, el foco queda en el encabezado (tabindex=-1)', async ({ page }) => {
  await mockStart(page, (r) => json(r, 200, progress(1)));
  await page.goto(URL_ONBOARDING);
  await expect(page.getByText('Paso 1 de 3')).toBeVisible();
  await expect(page.locator(':focus')).toHaveAttribute('tabindex', '-1');
});
```

---

## 10. Resultados reales

| Grupo | Casos | Resultado |
|---|---|---|
| Unitarias | UT-01…UT-07 | **NO EJECUTADO** (sin salida real) |
| API E1 | AP-01…AP-14 | **NO EJECUTADO** |
| Persistencia | PE-01…PE-06 | **NO EJECUTADO** |
| UI | UI-01…UI-12 | **NO EJECUTADO** |
| Bloqueados | BL-01…BL-09 | **BLOQUEADO** |

No se reporta ninguna prueba como aprobada ni fallida. El código no ha sido compilado.

---

## 11. Limitaciones y siguientes pasos

**Limitaciones:**
1. No se vio el repositorio, así que la compilación, los nombres de columnas, el esquema real, el framework de pruebas y la ruta de la pantalla son supuestos.
2. Solo E1 está implementado y es verificable. C2 (en servidor), C3, C4 (fin de paso y cierre), C5 y C6 no se pueden verificar con los artefactos actuales.
3. Las pruebas de UI usan E1 simulado. No prueban la integración real con el backend, la autenticación ni la navegación real a pagos.
4. Los casos AP-01 y AP-12 dependen de un catálogo provisional de pasos.
5. No hay pruebas de rendimiento, carga sostenida ni seguridad avanzada. No hay forma de forzar un 500 de E1 sin inyección de fallos.
6. Contraste y lector de pantalla requieren verificación manual (BL-08).

**Necesito de las otras etapas para desbloquear:**
- **Arquitectura:** contrato completo (E1 completo, GET de progreso, envío de paso, completar, reporte), semántica de `Idempotency-Key`, base de `currentStepIndex` y valores de `ErrorResponse.code`.
- **Datos:** modelo de persistencia aprobado (tipos, NOT NULL, CHECK, índices), tabla/destino de eventos, definiciones de MAU, CAC y mediana, y fuente del gasto de adquisición.
- **Backend:** contrato de `POST /kyc/verifications`, endpoints restantes, eventos de fin de paso y cierre, y cómo observar `onboarding_started`.
- **Frontend:** componente real (ruta y selectores estables), definición de campos de los pasos y cómo se provee `ONBOARDING_STEP_FIELDS`.
- **Producto/Cumplimiento:** requisitos de KYC y catálogo definitivo de pasos.