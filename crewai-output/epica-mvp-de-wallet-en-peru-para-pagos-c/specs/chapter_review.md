# Revisión final del Chapter Lead: MVP de wallet en Perú

**Fecha:** 2026-11-06
**Épica:** `packages/epic/peru-wallet-mvp.sp.md` (historias #2–#7, fundación técnica #8–#11)

## 1. Resumen

**Base de la revisión.** Solo he contrastado lo que consta en las entregas aprobadas: Arquitectura, Backend, Frontend, Pruebas y Seguridad.

- Las cinco entregas llegaron **truncadas** a esta revisión y no hay entrega de **Datos**.
- No he visto el código fuente ni `packages/bcp-wallet-mock-openapi.yaml`.
- Las limitaciones declaradas por cada rol coinciden con lo anterior.
- No doy por válido nada que dependa de contenido no visible.

**Alcance realmente entregado.** Es un único corte vertical: crear y consultar pagos de bajo monto con idempotencia.

- Backend: `POST /api/v1/payments` con `Idempotency-Key` y `GET /api/v1/payments/{paymentId}`.
- Frontend: `/pagos/nuevo` y `/pagos/:paymentId`.

**No implementado según las propias entregas:**
- onboarding (#2);
- vista de comercio/QR (#4);
- conciliación (#5);
- reversos y fallas (#7, solo parcial);
- adaptador HTTP del mock (#9);
- persistencia de analytics/onboarding (#11);
- job de sincronización de eventos;
- autenticación y autorización;
- AGENTS.md y plantilla de PR (#10, no consta entrega).

**Estado de verificación.** Backend, Frontend y Pruebas declaran **NO EJECUTADO**. No existe ninguna salida real de compilación, pruebas, arranque del BFF ni llamadas al mock. Por tanto, ningún criterio está verificado en ejecución.

**Seguridad.** El informe de seguridad registra 1 hallazgo Crítico, 3 Altos, 6 Medios y 1 Bajo, y recomienda **no liberar a usuarios reales** hasta cerrar S-01 a S-04. No consta ninguna corrección verificable de esos hallazgos.

**Conclusión.** La entrega es un buen punto de partida de diseño, pero no es integrable por tres motivos:

- hay criterios sin cubrir;
- hay contratos entre capas sin confirmar o incompatibles;
- hay hallazgos de severidad Alta/Crítica abiertos.

## 2. Matriz de criterios

El Issue no trae criterios de aceptación numerados. Uso las historias #2–#11 y los criterios derivados CA-1…CA-13 de Backend, que **no son oficiales** y deben ser confirmados por producto.

### 2.1 Historias y fundación técnica

| Criterio | Estado | Evidencia |
|---|---|---|
| #2 Registro y activación | **Sin cubrir** | Backend §1 y §4: onboarding no implementado. Frontend: fuera de entregable. Arquitectura solo lo diseña. |
| #3 Confirmación de pagos de bajo monto | **Parcial** | Backend CA-6/CA-8 y Frontend `/pagos/*`. Las transiciones asíncronas dependen del sync de eventos, que no existe. Sin ejecución. |
| #4 Estado de pagos QR para comercios | **Sin cubrir** | Backend y Frontend declaran vista de comercio/QR no implementada. |
| #5 Conciliación operativa | **Sin cubrir** | Backend §1: no implementado. Sin entrega de Datos para las instantáneas. |
| #6 Idempotencia en pagos | **Parcial** | Backend CA-1…CA-4 "Cubierto" solo en diseño/código sin ejecutar. Hay una ventana de duplicidad reconocida (Backend CA-2, §6) y la idempotencia del mock es desconocida ([PENDIENTE-OPENAPI]). Seguridad S-04 sigue abierto. |
| #7 Manejo de fallas y reversos | **Parcial / sin cubrir** | Solo CA-11 (el pago queda `PENDING` si el mock no responde). Reversos y disputas no implementados. Códigos de fallo de dominio sin lista (Frontend §5). |
| #8 Stack y estructura base | **Parcial** | Quarkus/Angular asumidos. Hay discrepancia de estructura con Arquitectura (ver 3.2). Sin evidencia de build. |
| #9 Adaptador del mock y verificación del grupo | **Sin cubrir** | `WalletBackendPort` sin implementación (Backend §1; Pruebas H7). Verificación del grupo 1 solo con pruebas de humo propuestas, sin salida. |
| #10 AGENTS.md y plantilla de PR | **Sin cubrir** | No consta ninguna entrega de estos artefactos. |
| #11 Persistencia propia del BFF | **Parcial** | Solo pagos e idempotencia (JDBC). Onboarding, analytics y configuración no implementados. Sin DDL de Datos. |

### 2.2 Criterios derivados (Backend)

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-1 `Idempotency-Key` obligatoria | Cubierto en diseño, **sin ejecutar** | Backend §4 (`PaymentService.validateKey`); Pruebas API-01/05/06/07. |
| CA-2 Reintento idéntico no duplica | Parcial | Backend §4 con "ventana" (§6, no visible); Pruebas API-02/15 sin ejecutar. |
| CA-3 Misma clave y solicitud distinta da 409 | Cubierto en diseño, **sin ejecutar** | Backend §3; Pruebas API-03/04. Riesgo de que el hash omita el destinatario (Pruebas H10, Seguridad S-06). |
| CA-4 Concurrencia da 409 `IDEMPOTENCY_IN_PROGRESS` | Cubierto en diseño, **sin ejecutar** | Backend §4; Pruebas API-15. |
| CA-5 Céntimos de PEN y límite | **Parcial** | El límite y su código de error no están publicados (Pruebas H8). Riesgo de coerción `12.5`→`12` (H11, S-07). |
| CA-6 Estados de dominio | Parcial | Backend §4; Frontend §4. Sin sync no hay transición asíncrona. |
| CA-7 `createdAt` del BFF | Cubierto en diseño, **sin ejecutar** | Backend §4 (D1). |
| CA-8 Consulta por id con 404 | Parcial | Sin vista por comercio. Sin control de propiedad (S-02). |
| CA-9 Validación en servidor (400) | Cubierto en diseño, **sin ejecutar** | `CreatePaymentRequest`, `ErrorMappers.mapValidation`. |
| CA-10 Errores sin trazas ni SQL | Cubierto en diseño, **sin ejecutar** | `ErrorMappers`, `RepositoryException`. |
| CA-11 Fallo del mock deja `PENDING` | Parcial | Sin sync no se resuelve; sin adaptador no hay llamada real. |
| CA-12 `EXPIRED` derivado | **Sin cubrir** | Backend §4: no cubierto. |
| CA-13 401/403 | **Sin cubrir** | Backend §4; Seguridad S-01. |
| UI-1 Mensajes por `code`/HTTP y reglas de clave | Cubierto en diseño, **sin ejecutar** | Frontend §2, §5. |

## 3. Hallazgos

### 3.1 Severidad Alta (bloquean la integración)

**R-01 · Alta: Historias y fundación sin cubrir**
- **Ubicación:** #2, #4, #5, #9, #10, #11 y CA-12/CA-13 (matriz 2.1 y 2.2).
- **Problema:** la épica cubre onboarding, QR/comercio, conciliación, instrumentación del funnel y el adaptador del mock. Nada de eso está entregado. Esa ausencia impide medir los objetivos de la épica: onboarding de 8 a 3 minutos y caída en checkout de 30% a 8%.
- **Corrección:**
  - Backend: implementar #9 primero, como adaptador `BcpWalletMockClient` apuntando a `https://bcp-wallet-mock.yefri.workers.dev/groups/1`.
  - Después, completar #2, #4, #5, #11 y el job de sincronización de eventos.
  - Entregar AGENTS.md y plantilla de PR (#10).
  - Si el alcance se reparte en varias entregas, producto debe confirmar por escrito qué criterios oficiales corresponden a esta.

**R-02 · Alta (Crítica en Seguridad): S-01, sin autenticación ni autorización**
- **Ubicación:** `PaymentResource` y todo `/api/v1`.
- **Problema:** endpoints que mueven dinero sin identificar al pagador. Frontend mapea 401/403 sin respaldo en el backend. Sigue abierto.
- **Corrección:**
  - Backend: aplicar deny-by-default con `quarkus-oidc` o `quarkus-smallrye-jwt`, validando firma, `iss`, `aud` y `exp`.
  - Derivar el pagador del token, nunca del cuerpo.
  - Definir roles y scopes.
  - Pruebas: añadir una prueba que recorra todas las rutas y exija 401 sin credenciales.
  - Frontend: no guardar el token en `localStorage`.

**R-03 · Alta: S-02, IDOR en `GET /payments/{paymentId}`**
- **Ubicación:** `PaymentResource.get`, `PaymentService.getPayment`.
- **Problema:** no hay control de propiedad y el formato del id no está confirmado.
- **Corrección:**
  - Backend: persistir `owner_id` y consultar con `WHERE payment_id=? AND owner_id=?`.
  - Devolver 404 si el pago no es del solicitante.
  - Usar UUIDv4.
  - Datos: añadir la columna e índice correspondientes.

**R-04 · Alta: S-03, eventos del mock sin autenticidad**
- **Ubicación:** `EventSyncService` y scheduler (aún no implementados); `/control/events`, `/callbacks/payments`.
- **Problema:** el mock no firma ni autentica, así que cualquier consumo puede ser suplantado.
- **Corrección:**
  - Backend: consumir solo desde la URL fija del grupo configurada.
  - Validar el esquema.
  - Aplicar transiciones de estado monótonas.
  - Verificar cada evento contra `GET` del pago en el mock antes de cambiar estado.
  - Registrar los cursores.

**R-05 · Alta: S-04, ventana de duplicidad o inconsistencia BFF↔mock**
- **Ubicación:** `PaymentService.createPayment/callBackend`, `IdempotencyRepository`.
- **Problema:** hay un fallo posible entre la llamada exitosa al mock y `updateOutcome/markCompleted`. No consta si el mock es idempotente. Una clave `PROCESSING` huérfana (por caída del proceso) devolvería `IDEMPOTENCY_IN_PROGRESS` indefinidamente. No consta ningún mecanismo de recuperación ni caducidad.
- **Corrección:**
  - Backend: enviar al mock una referencia externa derivada de `paymentId`, si el OpenAPI lo permite.
  - Añadir un proceso de recuperación para los `PROCESSING`/`PENDING` antiguos que consulte el mock antes de reintentar.
  - Definir un timeout de `PROCESSING`.
  - Arquitectura: confirmar contra el OpenAPI si el mock admite idempotencia o referencia externa.
  - Pruebas: añadir caso de caída entre la llamada al mock y el cierre de la clave.

**R-06 · Alta: contrato entre capas no confirmado o incompatible**
- **Ubicación:** `POST /payments`, `GET /payments/{id}`, códigos de error.
- **Problema:**
  - Las rutas y los códigos 201/200/400/404/409 son [SUPUESTO] de Backend.
  - Frontend usa `payeeId` como campo [SUPUESTO PENDIENTE], y Backend no lo ha confirmado.
  - El campo `paymentId` de la respuesta se infiere de la ruta (Pruebas H5).
  - El límite de monto y su código de error no se han publicado.
  - La lista completa de `code` de error no se ha entregado.
  - Frontend mapea 401/403, que el backend no emite.
  - Frontend mapea 404 en creación, que el contrato no define.
- **Corrección:**
  - Arquitectura: publicar un contrato único (OpenAPI del BFF) con rutas, DTOs, campos, límite y catálogo completo de `code` y estados HTTP.
  - Backend y Frontend: alinearse a ese documento.
  - Pruebas: sustituir los parámetros `-Dbff.payeeField` y `-Dbff.extraJson` por el contrato real.

**R-07 · Alta: pruebas sin ejecución**
- **Ubicación:** `testing_report.md` completo; Backend §5; Frontend §8.
- **Problema:**
  - Todos los casos figuran como NO EJECUTADO y no hay salida adjunta.
  - Las secciones de comandos de Backend y Frontend no son visibles para esta revisión.
  - Un caso omitido por `Assumptions` no cuenta como aprobado.
  - Todo queda **pendiente**.
- **Corrección:**
  - Backend, Frontend y Pruebas: ejecutar y adjuntar la salida real de:
    - compilación y pruebas del backend (por ejemplo `./mvnw verify`);
    - `ng build` y `ng test`;
    - la suite de API contra un BFF levantado.
  - Respetar la coordinación sobre `POST /control/reset`, que es compartido.

### 3.2 Severidad Media

**R-08 · Media: desviación respecto a la arquitectura hexagonal**
- **Ubicación:** estructura `pe.bcp.login.{resource,service,repository,dto,model}` frente al diseño `domain/application/infrastructure` de Arquitectura. Pruebas H2 también lo señala.
- **Problema:**
  - `WalletBackendPort`, `DomainException` y `ErrorCode` viven en el paquete `service`.
  - El nombre de paquete `login` no refleja el dominio de wallet.
  - Los casos de uso por puerto de entrada propuestos por Arquitectura no existen.
  - Los puertos de repositorio son clases JDBC concretas, sin interfaz.
- **Corrección:**
  - Backend: mover los puertos a `domain/port`.
  - Separar la implementación JDBC en `infrastructure/adapter/output/persistence`.
  - Renombrar el paquete raíz.
  - Arquitectura: aprobar la estructura final en #8.

**R-09 · Media: ausencia del modelo de Datos**
- **Ubicación:** tablas de idempotencia y pagos, columnas e índices.
- **Problema:** no hay DDL ni migraciones. Solo se infiere `idempotency_key` con unicidad por el `ON CONFLICT`.
- **Corrección:**
  - Datos: entregar el DDL versionado (por ejemplo, Flyway) con las restricciones de unicidad, `owner_id`, `request_hash`, `created_at` y los estados.
  - Datos: definir la retención.
  - Backend: alinear el repositorio al DDL.

**R-10 · Media: S-05, S-06, S-07, S-08, S-09, S-10 abiertos**
- **Ubicación:** informe de seguridad §2.
- **Problema:**
  - Clave de idempotencia global, no ligada al usuario.
  - `requestHash` posiblemente sin destinatario.
  - Coerción `12.5`→`12` y límite no publicado.
  - Sin límite de tasa.
  - Sin pista de auditoría.
  - `POST /control/reset` expuesto y compartido.
- **Corrección:**
  - Backend: usar clave única compuesta `(owner_id, idempotency_key)`.
  - Incluir `payeeId`, importe y moneda en el hash.
  - Desactivar la coerción de flotantes a entero en Jackson.
  - Aplicar límite de tasa.
  - Añadir un registro de auditoría de operaciones financieras.
  - El adaptador no debe invocar `/control/reset`.
  - Documentar su uso coordinado en AGENTS.md.

**R-11 · Media: clave de idempotencia solo en memoria del cliente**
- **Ubicación:** Frontend §2.
- **Problema:** la clave se conserva en memoria y nunca en `localStorage`. Eso es correcto por seguridad. Pero si el usuario recarga la pestaña tras un error de red, se pierde la clave y un reintento podría duplicar el cobro. El diseño no lo contempla.
- **Corrección:**
  - Frontend y Backend: avisar al usuario de verificar el estado del pago antes de reintentar tras una recarga.
  - Valorar `sessionStorage` solo para la clave, con aprobación de Seguridad.
  - Valorar un endpoint de consulta por clave.

**R-12 · Media: validaciones de cliente, bordes y estados**
- **Ubicación:** Frontend §2/§4; Backend CA-5, CA-6.
- **Problema:** el cliente no valida el límite máximo. El límite de 7 dígitos enteros en céntimos no está conectado al límite del servidor. El listado vacío no tiene endpoint.
- **Corrección:** publicar el límite en el contrato y reflejarlo en el cliente. Definir el endpoint de listado, que Arquitectura ya prevé con `ListPaymentsUseCase`.

### 3.3 Severidad Baja

**R-13 · Baja: S-11, sin SCA ni versiones fijadas**
- **Ubicación:** build de Quarkus y Angular.
- **Corrección:**
  - Añadir un análisis de dependencias en CI.
  - Fijar versiones.
  - Revisar la dependencia `quarkus-narayana-jta` junto con JDBC directo, para confirmar que es necesaria y no hay duplicación con otro mecanismo transaccional.

**R-14 · Baja: inconsistencia de nombre de ruta**
- **Ubicación:** el informe de seguridad cita `packages/epic/peru-wallet-mock-mvp`, mientras la fuente del Issue es `packages/epic/peru-wallet-mvp.sp.md`.
- **Corrección:** Seguridad: corregir la referencia.

## 4. Mantenibilidad y riesgos de operación

**Deuda técnica**
- Estructura de paquetes divergente (R-08).
- Repositorios sin puertos.
- Supuestos de contrato sin resolver.

**Operación**
- Sin adaptador y sin sync, el BFF no puede completar pagos reales. Los pagos quedarían en `PENDING` sin resolución.
- Sin proceso de recuperación para `PROCESSING` huérfanos.
- El grupo 1 del mock es compartido, y `POST /control/reset` borra sus datos. Hay riesgo de interferir con otros equipos o agentes.
- No se ha definido observabilidad: logs con `traceId`, métricas ni alertas.
- Despliegue y CI no constan.

## 5. Pendientes antes de integrar

1. Confirmar con producto los criterios de aceptación oficiales de la épica.
2. Entregar el modelo de Datos (DDL versionado).
3. Publicar el contrato final del BFF (OpenAPI) y alinear Backend, Frontend y Pruebas.
4. Implementar #9, el adaptador del mock, y verificar el grupo 1 con salida real.
5. Implementar autenticación y autorización, y cerrar S-01 a S-04 con verificación.
6. Implementar sync de eventos, `EXPIRED` derivado (CA-12) y recuperación de `PROCESSING`.
7. Cubrir #2, #4, #5, #7 y #11, o dejar documentado el recorte de alcance con aprobación humana.
8. Entregar AGENTS.md y plantilla de PR (#10).
9. Ejecutar y adjuntar la salida real de build y pruebas de backend, frontend y API.
10. Alinear la arquitectura hexagonal (R-08).

## 6. Correcciones necesarias por rol

- **Arquitecto:**
  - Publicar el contrato OpenAPI del BFF con rutas, DTOs, campos (`payeeId`, `paymentId`), límite de monto y catálogo de `code`.
  - Verificar el OpenAPI del mock sobre idempotencia y referencias externas.
  - Aprobar la estructura final de paquetes.
- **Datos:**
  - Entregar el DDL con `owner_id`, `(owner_id, idempotency_key)`, `request_hash`, `created_at`, estados y retención.
  - Cubrir las tablas de onboarding, analytics, instantáneas, cursores y configuración (#11).
- **Backend:**
  - Implementar #9, autenticación, `owner_id`, hash completo, recuperación de `PROCESSING`, sync de eventos, `EXPIRED`, onboarding, QR/comercio, conciliación y reversos.
  - Mover los puertos al dominio.
  - Configurar Jackson sin coerción.
  - Añadir límite de tasa y auditoría.
- **Frontend:**
  - Alinear el campo de destinatario y los códigos al contrato final.
  - Manejar la pérdida de clave tras recarga.
  - Implementar las pantallas de onboarding, comercio y conciliación cuando existan los endpoints.
  - No almacenar tokens en `localStorage`.
- **Pruebas:**
  - Ejecutar todo lo marcado NO EJECUTADO y adjuntar la salida real.
  - Sustituir los parámetros supuestos por el contrato.
  - Añadir pruebas de 401/403, IDOR, caída entre mock y cierre de clave y coerción numérica.
  - Coordinar `POST /control/reset`.
- **Seguridad:**
  - Verificar con evidencia la resolución de S-01 a S-10.
  - Corregir la referencia de ruta (R-14).
  - Revisar el código real una vez entregado.

**VEREDICTO: RECHAZADO**