# backend_spec.md

> **Estado:** código entregado **sin compilar y sin ejecutar** (NO EJECUTADO). No afirmo que compile, funcione ni sea seguro hasta que se ejecuten los comandos de la sección 5.
>
> **Limitaciones de la información recibida:**
> - El contrato de Arquitectura llegó **truncado**: no vi las rutas, los esquemas ni los códigos de estado detallados.
> - No recibí el modelo de Datos ni el código existente del repositorio.
> - No recibí `packages/bcp-wallet-mock-openapi.yaml`.
> - El Issue es la épica; no trae criterios de aceptación numerados.
> - Todo lo que no está sustentado en lo recibido se declara como **[SUPUESTO]** o **[PENDIENTE]** en la sección 6.

---

## 1) Resumen de la implementación

**Alcance entregado (corte vertical):** creación y consulta de pagos de bajo monto con **idempotencia obligatoria**. Cubre lo que el contrato recibido sí dice: BFF en `/api/v1`, `POST /payments` con header `Idempotency-Key`, unicidad en base de datos, estrategia "insertar primero" (D4), importes enteros en céntimos de PEN (D7), estados de dominio (`PENDING`, `CONFIRMED`, `FAILED`, `EXPIRED`, `REVERSED`) y formato de error `code`, `message`, `retryable`, `traceId`.

**Endpoints (rutas y códigos de estado marcados como [SUPUESTO] donde el contrato truncado no me los confirmó):**

| Método y ruta | Resultado |
|---|---|
| `POST /api/v1/payments` | `201` pago nuevo; `200` repetición idempotente; `400` validación; `409` conflicto de idempotencia |
| `GET /api/v1/payments/{paymentId}` | `200`; `400` id inválido; `404` no encontrado |

**Capas:**
- `resource`: HTTP y mapeo de errores.
- `service`: lógica de negocio, idempotencia y puerto hacia el mock.
- `repository`: JDBC con `PreparedStatement` parametrizado.
- `dto`: entrada y salida.
- `model`: registros y enums compartidos.

**No implementado** (ver sección 4 y 6): onboarding (#2), vista de comercio (#4), conciliación (#5), reversos (#7), persistencia de analytics (#11), adaptador HTTP del mock (#9), autenticación 401/403 y job de sincronización de eventos.

**Dependencias Quarkus asumidas** (stack final depende de #8): `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-agroal`, `quarkus-jdbc-postgresql`, `quarkus-narayana-jta`.

---

## 2) Clases y responsabilidades

| Paquete | Clase | Responsabilidad |
|---|---|---|
| `pe.bcp.login.resource` | `PaymentResource` | Endpoints HTTP, lee `Idempotency-Key`, dispara validación de entrada, elige 200 o 201 |
| `pe.bcp.login.resource` | `ErrorMappers` | Traduce excepciones al formato `code`, `message`, `retryable`, `traceId` sin trazas ni SQL |
| `pe.bcp.login.dto` | `CreatePaymentRequest` | Entrada con Bean Validation |
| `pe.bcp.login.dto` | `PaymentResponse` | Salida del pago (sin `backendPaymentId` ni `payerWalletId`) |
| `pe.bcp.login.dto` | `ErrorResponse` | Formato único de error |
| `pe.bcp.login.service` | `PaymentService` | Idempotencia, límites, orquestación con el puerto del mock, estados |
| `pe.bcp.login.service` | `WalletBackendPort` | Puerto de salida hacia el mock (implementación pendiente en #9) |
| `pe.bcp.login.service` | `PaymentCommand`, `BackendPaymentCommand`, `BackendPaymentResult`, `CreatePaymentOutcome` | Objetos de transferencia internos |
| `pe.bcp.login.service` | `DomainException`, `ErrorCode`, `BackendUnavailableException` | Errores de negocio y de infraestructura del puerto |
| `pe.bcp.login.repository` | `IdempotencyRepository` | Inserta primero con `ON CONFLICT DO NOTHING`, consulta y completa la clave |
| `pe.bcp.login.repository` | `PaymentRecordRepository` | Inserta, actualiza resultado y consulta pagos del BFF |
| `pe.bcp.login.repository` | `RepositoryException` | Envuelve `SQLException` sin exponer SQL |
| `pe.bcp.login.model` | `PaymentRecord`, `IdempotencyRecord`, `PaymentStatus`, `IdempotencyStatus` | Modelo de persistencia y estados |

---

## 3) Recorrido de un caso válido y uno de error

### Caso válido: `POST /api/v1/payments` con clave nueva

1. El cliente envía `Idempotency-Key: pay-2026-0001` y un body válido.
2. `PaymentResource.create` recibe el body validado (`@NotNull @Valid`).
3. `PaymentService.createPayment` valida la clave (`^[A-Za-z0-9_-]{8,64}$`), valida el límite de monto, calcula el hash SHA-256 de la solicitud y genera `paymentId`.
4. `IdempotencyRepository.tryInsert` ejecuta `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` (estado `PROCESSING`). Devuelve `true` y la transacción se confirma antes de llamar al mock.
5. `PaymentRecordRepository.insert` guarda el pago en `PENDING` con `createdAt` del BFF (D1).
6. `WalletBackendPort.createPayment` llama al mock a través del adaptador (pendiente #9) y devuelve el estado de dominio.
7. `PaymentRecordRepository.updateOutcome` guarda el estado final y `IdempotencyRepository.markCompleted` cierra la clave.
8. `PaymentResource` responde `201` con `PaymentResponse`.

### Caso de error: la misma clave con un monto distinto

1. Mismo header `Idempotency-Key: pay-2026-0001`, pero `amountCents` distinto.
2. `PaymentService` calcula un hash distinto y `tryInsert` devuelve `false` (conflicto de unicidad).
3. `IdempotencyRepository.findByKey` devuelve el registro previo y `requestHash` no coincide.
4. Se lanza `DomainException(IDEMPOTENCY_KEY_REUSED)`.
5. `ErrorMappers.mapDomain` responde `409`:

```json
{"code":"IDEMPOTENCY_KEY_REUSED","message":"La clave de idempotencia ya fue usada con una solicitud distinta.","retryable":false,"traceId":"<uuid>"}
```

No se llama al mock y no se crea ningún pago nuevo. Si la solicitud es idéntica y la clave está `COMPLETED`, se devuelve el pago original con `200`.

---

## 4) Trazabilidad criterio de aceptación -> código

El Issue recibido no incluye criterios numerados. Uso **criterios derivados del texto de la épica y del contrato** (no son criterios oficiales; deben confirmarse).

| Criterio derivado | Fuente | Cobertura | Clase y método |
|---|---|---|---|
| CA-1: `POST /payments` exige `Idempotency-Key` | Contrato §1.2 punto 3, #6 | Cubierto | `PaymentService.validateKey` |
| CA-2: un reintento con la misma clave y la misma solicitud no duplica el cargo | #6, riesgo de duplicidad | Cubierto (con la ventana descrita en la sección 6) | `PaymentService.createPayment` y `replay`; `IdempotencyRepository.tryInsert` |
| CA-3: la misma clave con una solicitud distinta da conflicto | #6 | Cubierto | `PaymentService.replay` -> `ErrorCode.IDEMPOTENCY_KEY_REUSED` |
| CA-4: reintento concurrente mientras se procesa | D4 | Cubierto | `PaymentService.replay` -> `IDEMPOTENCY_IN_PROGRESS` (409, `retryable=true`) |
| CA-5: importes enteros en céntimos de PEN; moneda fija `PEN` | D7 | Cubierto | `CreatePaymentRequest` (`@Positive`, `@Pattern`); `PaymentService` (límite) |
| CA-6: estados de dominio claros | §1.2 punto 4, #3 | Parcial: se almacenan y exponen; la transición asíncrona posterior requiere el sync de eventos | `PaymentStatus`; `PaymentService.callBackend` |
| CA-7: el BFF guarda `createdAt` | D1 | Cubierto | `PaymentService.createPayment`; `PaymentRecordRepository.insert` |
| CA-8: consulta de un pago por id, con 404 si no existe | #3, #4 | Parcial: no hay vista por comercio | `PaymentResource.get`; `PaymentService.getPayment` |
| CA-9: validación de entrada en servidor con 400 | Tarea | Cubierto | `CreatePaymentRequest`; `ErrorMappers.mapValidation` |
| CA-10: errores sin trazas ni SQL, con formato único | §1.2 punto 5 | Cubierto | `ErrorMappers`; `RepositoryException` |
| CA-11: fallo del mock sin respuesta | #7 | Parcial: el pago queda `PENDING`; sin sync no se resuelve | `PaymentService.callBackend` (captura `BackendUnavailableException`) |
| CA-12: expirado derivado (`QR_EXPIRED` o timeout) | D2 | **No cubierto** (el mapeo de `QR_EXPIRED` pertenece al adaptador #9; el timeout de `PENDING` requiere el sync) | n/a |
| CA-13: 401/403 | Tarea | **No cubierto**: el contrato recibido no define autenticación del BFF | n/a |
| Historia #2 onboarding | Issue | **No cubierto** | n/a |
| Historia #4 estado QR para comercios | Issue | **No cubierto** | n/a |
| Historia #5 conciliación | Issue | **No cubierto** | n/a |
| Historia #7 reversos | Issue, D6 | **No cubierto** (depende de que el mock ofrezca reverso) | n/a |
| #9 adaptador del mock | Issue | **No cubierto** (solo se define el puerto) | `WalletBackendPort` |
| #11 persistencia de onboarding, analytics y configuración | Issue | **No cubierto** (solo pagos e idempotencia) | n/a |

---

## 5) Comandos de verificación (NO EJECUTADO)

| Comando | Estado |
|---|---|
| `mvn -q clean compile` | NO EJECUTADO |
| `mvn -q test` | NO EJECUTADO |
| `mvn -q quarkus:dev` | NO EJECUTADO |
| `curl -i -X POST http://localhost:8080/api/v1/payments -H 'Content-Type: application/json' -H 'Idempotency-Key: pay-2026-0001' -d '{"payerWalletId":"w-1","destination":"merchant-demo","amountCents":1500,"currency":"PEN"}'` | NO EJECUTADO |
| Repetir el `curl` anterior (esperado: `200` con el mismo `paymentId`) | NO EJECUTADO |
| Repetir con `amountCents` distinto (esperado: `409 IDEMPOTENCY_KEY_REUSED`) | NO EJECUTADO |
| `curl -i http://localhost:8080/api/v1/payments/no-existe` (esperado: `404`) | NO EJECUTADO |

**Pruebas que faltan y no escribí:**
- Concurrencia de dos solicitudes con la misma clave.
- Comportamiento de `@ServerExceptionMapper` frente al mapper de violaciones por defecto de Quarkus.
- Compatibilidad de los tipos `OffsetDateTime` con el esquema real.

> No ejecuto `POST /control/reset` en ningún paso: borra datos compartidos del grupo 1.

---

## 6) Supuestos y pendientes para otros roles

**Para Datos** (no recibí el modelo; **no redefino ni regenero migraciones**). Asumo que existen estas tablas y columnas, y que el motor es PostgreSQL:
- `idempotency_record(idempotency_key PK/UNIQUE, request_hash, status, payment_id, created_at timestamptz)`
- `payment_record(payment_id PK, backend_payment_id, payer_wallet_id, destination, amount_cents bigint, currency, description, status, failure_code, created_at timestamptz, updated_at timestamptz)`

Si los nombres, tipos o el motor difieren, hay que ajustar solo las constantes SQL de los repositorios. Falta además un índice o unicidad sobre `idempotency_key` (imprescindible para `ON CONFLICT`).

**Para Arquitectura:**
- Confirmar rutas, campos del body (`payerWalletId`, `destination`, `amountCents`, `currency`, `description` son supuestos míos) y códigos de estado (201 y 200 son supuestos).
- Confirmar el modelo de autenticación del BFF (401/403).
- Confirmar el monto máximo de "bajo monto". Usé `wallet.payment.max-amount-cents` con valor por defecto **50000** (PEN 500), que es un supuesto mío.

**Para quien implemente #9 (adaptador del mock):**
- Implementar `WalletBackendPort` como bean CDI. **Sin esa implementación la aplicación no arranca** (falta un bean para inyectar).
- Mapear los estados y códigos del mock a `PaymentStatus` (por ejemplo `QR_EXPIRED` a `EXPIRED`), apuntando a `/groups/1`.
- Lanzar `BackendUnavailableException` en timeout o 5xx.
- Normalizar `failureCode` a códigos de dominio, porque se expone tal cual en `PaymentResponse`.
- Confirmar si el mock es idempotente ([PENDIENTE-OPENAPI]).

**Limitaciones de diseño conocidas:**
- Hay una ventana entre `tryInsert` y `PaymentRecordRepository.insert` (dos transacciones). Si el proceso cae ahí, la clave queda en `PROCESSING` y los reintentos reciben 409 `IDEMPOTENCY_IN_PROGRESS`. Se necesita un job de recuperación.
- Si el mock no responde, el pago queda `PENDING` y la clave `COMPLETED`. La resolución depende del job de sincronización de `/control/events` y `/callbacks/payments`, que no está implementado.
- Si falla la actualización del resultado tras un éxito del mock, el pago queda `PENDING` en la base y la clave en `PROCESSING`.
- El paquete base `pe.bcp.login` viene impuesto por el formato de entrega solicitado. No coincide con el dominio wallet; conviene renombrarlo cuando #8 fije la estructura.

**Para Frontend:**
- Tratar `409` con `retryable=true` como "reintentar con la misma clave".
- Generar una clave nueva por intención de pago.

---

## Código Java

```java
// PaymentStatus.java
package pe.bcp.login.model;

/**
 * Estados de dominio del pago, estables e independientes del mock.
 */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    EXPIRED,
    REVERSED
}
```

```java
// IdempotencyStatus.java
package pe.bcp.login.model;

/**
 * Estado del registro de idempotencia.
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED
}
```

```java
// PaymentRecord.java
package pe.bcp.login.model;

import java.time.Instant;

/**
 * Pago persistido por el BFF. Los importes son enteros en céntimos de PEN.
 */
public record PaymentRecord(
        String paymentId,
        String backendPaymentId,
        String payerWalletId,
        String destination,
        long amountCents,
        String currency,
        String description,
        PaymentStatus status,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public PaymentRecord withOutcome(PaymentStatus newStatus,
                                     String newBackendPaymentId,
                                     String newFailureCode,
                                     Instant newUpdatedAt) {
        return new PaymentRecord(
                paymentId,
                newBackendPaymentId,
                payerWalletId,
                destination,
                amountCents,
                currency,
                description,
                newStatus,
                newFailureCode,
                createdAt,
                newUpdatedAt);
    }
}
```

```java
// IdempotencyRecord.java
package pe.bcp.login.model;

import java.time.Instant;

/**
 * Registro de idempotencia asociado a una clave enviada por el cliente.
 */
public record IdempotencyRecord(
        String idempotencyKey,
        String requestHash,
        IdempotencyStatus status,
        String paymentId,
        Instant createdAt) {
}
```

```java
// RepositoryException.java
package pe.bcp.login.repository;

/**
 * Error de persistencia. No transporta SQL ni datos de la solicitud.
 */
public class RepositoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
// IdempotencyRepository.java
package pe.bcp.login.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.bcp.login.model.IdempotencyRecord;
import pe.bcp.login.model.IdempotencyStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Optional;

/**
 * Acceso a datos de idempotencia. Todas las consultas son parametrizadas.
 * Los nombres de tabla y columna son un SUPUESTO pendiente de confirmar con Datos.
 */
@ApplicationScoped
public class IdempotencyRepository {

    private static final String INSERT_SQL =
            "INSERT INTO idempotency_record "
                    + "(idempotency_key, request_hash, status, payment_id, created_at) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (idempotency_key) DO NOTHING";

    private static final String SELECT_SQL =
            "SELECT idempotency_key, request_hash, status, payment_id, created_at "
                    + "FROM idempotency_record WHERE idempotency_key = ?";

    private static final String COMPLETE_SQL =
            "UPDATE idempotency_record SET status = ? WHERE idempotency_key = ?";

    private final AgroalDataSource dataSource;

    @Inject
    public IdempotencyRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Inserta la clave en estado PROCESSING.
     *
     * @return true si se insertó; false si la clave ya existía.
     */
    @Transactional
    public boolean tryInsert(String idempotencyKey, String requestHash, String paymentId, Instant createdAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, idempotencyKey);
            statement.setString(2, requestHash);
            statement.setString(3, IdempotencyStatus.PROCESSING.name());
            statement.setString(4, paymentId);
            statement.setObject(5, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo registrar la clave de idempotencia", e);
        }
    }

    @Transactional
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, idempotencyKey);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                return Optional.of(new IdempotencyRecord(
                        rs.getString("idempotency_key"),
                        rs.getString("request_hash"),
                        IdempotencyStatus.valueOf(rs.getString("status")),
                        rs.getString("payment_id"),
                        createdAt == null ? null : createdAt.toInstant()));
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo consultar la clave de idempotencia", e);
        }
    }

    @Transactional
    public void markCompleted(String idempotencyKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COMPLETE_SQL)) {
            statement.setString(1, IdempotencyStatus.COMPLETED.name());
            statement.setString(2, idempotencyKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo completar la clave de idempotencia", e);
        }
    }
}
```

```java
// PaymentRecordRepository.java
package pe.bcp.login.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.bcp.login.model.PaymentRecord;
import pe.bcp.login.model.PaymentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Acceso a datos de pagos del BFF. Consultas parametrizadas.
 * Los nombres de tabla y columna son un SUPUESTO pendiente de confirmar con Datos.
 */
@ApplicationScoped
public class PaymentRecordRepository {

    private static final String INSERT_SQL =
            "INSERT INTO payment_record "
                    + "(payment_id, backend_payment_id, payer_wallet_id, destination, amount_cents, "
                    + "currency, description, status, failure_code, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_OUTCOME_SQL =
            "UPDATE payment_record SET status = ?, backend_payment_id = ?, failure_code = ?, updated_at = ? "
                    + "WHERE payment_id = ?";

    private static final String SELECT_SQL =
            "SELECT payment_id, backend_payment_id, payer_wallet_id, destination, amount_cents, "
                    + "currency, description, status, failure_code, created_at, updated_at "
                    + "FROM payment_record WHERE payment_id = ?";

    private final AgroalDataSource dataSource;

    @Inject
    public PaymentRecordRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public void insert(PaymentRecord payment) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, payment.paymentId());
            setNullableString(statement, 2, payment.backendPaymentId());
            statement.setString(3, payment.payerWalletId());
            statement.setString(4, payment.destination());
            statement.setLong(5, payment.amountCents());
            statement.setString(6, payment.currency());
            setNullableString(statement, 7, payment.description());
            statement.setString(8, payment.status().name());
            setNullableString(statement, 9, payment.failureCode());
            statement.setObject(10, OffsetDateTime.ofInstant(payment.createdAt(), ZoneOffset.UTC));
            statement.setObject(11, OffsetDateTime.ofInstant(payment.updatedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo guardar el pago", e);
        }
    }

    @Transactional
    public void updateOutcome(String paymentId,
                              PaymentStatus status,
                              String backendPaymentId,
                              String failureCode,
                              Instant updatedAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_OUTCOME_SQL)) {
            statement.setString(1, status.name());
            setNullableString(statement, 2, backendPaymentId);
            setNullableString(statement, 3, failureCode);
            statement.setObject(4, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
            statement.setString(5, paymentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo actualizar el pago", e);
        }
    }

    @Transactional
    public Optional<PaymentRecord> findById(String paymentId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, paymentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
                return Optional.of(new PaymentRecord(
                        rs.getString("payment_id"),
                        rs.getString("backend_payment_id"),
                        rs.getString("payer_wallet_id"),
                        rs.getString("destination"),
                        rs.getLong("amount_cents"),
                        rs.getString("currency"),
                        rs.getString("description"),
                        PaymentStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_code"),
                        createdAt == null ? null : createdAt.toInstant(),
                        updatedAt == null ? null : updatedAt.toInstant()));
            }
        } catch (SQLException e) {
            throw new RepositoryException("No se pudo consultar el pago", e);
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
```

```java
// ErrorCode.java
package pe.bcp.login.service;

/**
 * Códigos de error de dominio con su estado HTTP asociado.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    IDEMPOTENCY_KEY_REQUIRED(400),
    AMOUNT_LIMIT_EXCEEDED(400),
    PAYMENT_NOT_FOUND(404),
    IDEMPOTENCY_KEY_REUSED(409),
    IDEMPOTENCY_IN_PROGRESS(409),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
```

```java
// DomainException.java
package pe.bcp.login.service;

/**
 * Error de negocio traducible a una respuesta HTTP coherente.
 * El mensaje debe ser apto para el usuario y no contener datos sensibles.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final boolean retryable;

    public DomainException(ErrorCode code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public ErrorCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
```

```java
// BackendUnavailableException.java
package pe.bcp.login.service;

/**
 * El backend (mock) no respondió a tiempo o devolvió un error de infraestructura.
 * El resultado del pago en el backend es desconocido.
 */
public class BackendUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
// PaymentCommand.java
package pe.bcp.login.service;

/**
 * Datos validados de una solicitud de pago. Importe en céntimos de PEN.
 */
public record PaymentCommand(
        String payerWalletId,
        String destination,
        long amountCents,
        String currency,
        String description) {
}
```

```java
// BackendPaymentCommand.java
package pe.bcp.login.service;

/**
 * Solicitud de pago hacia el backend. paymentId es la referencia del BFF.
 */
public record BackendPaymentCommand(
        String paymentId,
        String payerWalletId,
        String destination,
        long amountCents,
        String currency,
        String description) {
}
```

```java
// BackendPaymentResult.java
package pe.bcp.login.service;

import pe.bcp.login.model.PaymentStatus;

/**
 * Resultado del backend ya traducido a estados de dominio por el adaptador.
 * failureCode debe estar normalizado a un código de dominio.
 */
public record BackendPaymentResult(
        String backendPaymentId,
        PaymentStatus status,
        String failureCode) {
}
```

```java
// WalletBackendPort.java
package pe.bcp.login.service;

/**
 * Puerto de salida hacia el BCP Wallet Mock (grupo 1).
 * La implementación pertenece al adaptador de la historia #9.
 */
public interface WalletBackendPort {

    /**
     * Crea un pago en el backend.
     *
     * @throws BackendUnavailableException si el backend no responde o falla por infraestructura.
     */
    BackendPaymentResult createPayment(BackendPaymentCommand command);
}
```

```java
// CreatePaymentOutcome.java
package pe.bcp.login.service;

import pe.bcp.login.model.PaymentRecord;

/**
 * Resultado de crear un pago. replayed es true si se devolvió un pago ya registrado
 * bajo la misma clave de idempotencia.
 */
public record CreatePaymentOutcome(PaymentRecord payment, boolean replayed) {
}
```

```java
// PaymentService.java
package pe.bcp.login.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pe.bcp.login.model.IdempotencyRecord;
import pe.bcp.login.model.IdempotencyStatus;
import pe.bcp.login.model.PaymentRecord;
import pe.bcp.login.model.PaymentStatus;
import pe.bcp.login.repository.IdempotencyRepository;
import pe.bcp.login.repository.PaymentRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lógica de negocio de pagos con idempotencia obligatoria.
 * Estrategia "insertar primero": se registra la clave en PROCESSING antes de llamar al backend.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final IdempotencyRepository idempotencyRepository;
    private final PaymentRecordRepository paymentRepository;
    private final WalletBackendPort backendPort;
    private final long maxAmountCents;

    @Inject
    public PaymentService(IdempotencyRepository idempotencyRepository,
                          PaymentRecordRepository paymentRepository,
                          WalletBackendPort backendPort,
                          @ConfigProperty(name = "wallet.payment.max-amount-cents", defaultValue = "50000")
                          long maxAmountCents) {
        this.idempotencyRepository = idempotencyRepository;
        this.paymentRepository = paymentRepository;
        this.backendPort = backendPort;
        this.maxAmountCents = maxAmountCents;
    }

    public CreatePaymentOutcome createPayment(String idempotencyKey, PaymentCommand command) {
        validateKey(idempotencyKey);
        if (command.amountCents() > maxAmountCents) {
            throw new DomainException(
                    ErrorCode.AMOUNT_LIMIT_EXCEEDED,
                    "El importe supera el límite permitido para pagos de bajo monto.",
                    false);
        }

        String requestHash = hashOf(command);
        Instant now = Instant.now();
        String paymentId = UUID.randomUUID().toString();

        boolean inserted = idempotencyRepository.tryInsert(idempotencyKey, requestHash, paymentId, now);
        if (!inserted) {
            return replay(idempotencyKey, requestHash);
        }

        PaymentRecord pending = new PaymentRecord(
                paymentId,
                null,
                command.payerWalletId(),
                command.destination(),
                command.amountCents(),
                command.currency(),
                command.description(),
                PaymentStatus.PENDING,
                null,
                now,
                now);
        paymentRepository.insert(pending);

        PaymentRecord result = callBackend(pending);
        idempotencyRepository.markCompleted(idempotencyKey);
        return new CreatePaymentOutcome(result, false);
    }

    public PaymentRecord getPayment(String paymentId) {
        if (paymentId == null || !PAYMENT_ID_PATTERN.matcher(paymentId).matches()) {
            throw new DomainException(
                    ErrorCode.VALIDATION_ERROR,
                    "El identificador de pago no es válido.",
                    false);
        }
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "No se encontró el pago solicitado.",
                        false));
    }

    private CreatePaymentOutcome replay(String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(idempotencyKey);
        if (existing.isEmpty()) {
            throw inProgress();
        }
        IdempotencyRecord record = existing.get();
        if (!record.requestHash().equals(requestHash)) {
            throw new DomainException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "La clave de idempotencia ya fue usada con una solicitud distinta.",
                    false);
        }
        if (record.status() != IdempotencyStatus.COMPLETED || record.paymentId() == null) {
            throw inProgress();
        }
        PaymentRecord payment = paymentRepository.findById(record.paymentId())
                .orElseThrow(PaymentService::inProgress);
        return new CreatePaymentOutcome(payment, true);
    }

    private PaymentRecord callBackend(PaymentRecord pending) {
        BackendPaymentCommand backendCommand = new BackendPaymentCommand(
                pending.paymentId(),
                pending.payerWalletId(),
                pending.destination(),
                pending.amountCents(),
                pending.currency(),
                pending.description());
        try {
            BackendPaymentResult result = backendPort.createPayment(backendCommand);
            PaymentStatus status = (result == null || result.status() == null)
                    ? PaymentStatus.PENDING
                    : result.status();
            String backendPaymentId = result == null ? null : result.backendPaymentId();
            String failureCode = result == null ? null : result.failureCode();
            Instant updatedAt = Instant.now();
            paymentRepository.updateOutcome(
                    pending.paymentId(), status, backendPaymentId, failureCode, updatedAt);
            return pending.withOutcome(status, backendPaymentId, failureCode, updatedAt);
        } catch (BackendUnavailableException e) {
            // Resultado desconocido: se deja PENDING para que la sincronización lo resuelva.
            LOG.warnf("Backend sin respuesta para paymentId=%s; el pago queda PENDING", pending.paymentId());
            return pending;
        }
    }

    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainException(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "El encabezado Idempotency-Key es obligatorio.",
                    false);
        }
        if (!KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new DomainException(
                    ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key debe tener entre 8 y 64 caracteres (letras, números, guion o guion bajo).",
                    false);
        }
    }

    private static DomainException inProgress() {
        return new DomainException(
                ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "La solicitud con esta clave de idempotencia aún se está procesando.",
                true);
    }

    private static String hashOf(PaymentCommand command) {
        String canonical = String.join("|",
                command.payerWalletId(),
                command.destination(),
                Long.toString(command.amountCents()),
                command.currency(),
                command.description() == null ? "" : command.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
```

```java
// CreatePaymentRequest.java
package pe.bcp.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pe.bcp.login.service.PaymentCommand;

/**
 * Entrada de POST /api/v1/payments. Los campos son un SUPUESTO pendiente de confirmar con Arquitectura.
 * Importe en céntimos de PEN.
 */
public record CreatePaymentRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 64, message = "debe tener máximo 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "contiene caracteres no permitidos")
        String payerWalletId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 64, message = "debe tener máximo 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_.@-]*$", message = "contiene caracteres no permitidos")
        String destination,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que 0")
        Long amountCents,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^PEN$", message = "solo se admite PEN")
        String currency,

        @Size(max = 140, message = "debe tener máximo 140 caracteres")
        String description) {

    public PaymentCommand toCommand() {
        String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();
        return new PaymentCommand(payerWalletId, destination, amountCents, currency, cleanDescription);
    }
}
```

```java
// PaymentResponse.java
package pe.bcp.login.dto;

import pe.bcp.login.model.PaymentRecord;
import pe.bcp.login.model.PaymentStatus;

import java.time.Instant;

/**
 * Salida del pago. No expone identificadores internos del backend ni de la billetera pagadora.
 * Importe en céntimos de PEN.
 */
public record PaymentResponse(
        String paymentId,
        PaymentStatus status,
        long amountCents,
        String currency,
        String destination,
        String description,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(PaymentRecord record) {
        return new PaymentResponse(
                record.paymentId(),
                record.status(),
                record.amountCents(),
                record.currency(),
                record.destination(),
                record.description(),
                record.failureCode(),
                record.createdAt(),
                record.updatedAt());
    }
}
```

```java
// ErrorResponse.java
package pe.bcp.login.dto;

/**
 * Formato único de error del BFF: code, message, retryable, traceId.
 */
public record ErrorResponse(
        String code,
        String message,
        boolean retryable,
        String traceId) {
}
```

```java
// PaymentResource.java
package pe.bcp.login.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import pe.bcp.login.dto.CreatePaymentRequest;
import pe.bcp.login.dto.PaymentResponse;
import pe.bcp.login.service.CreatePaymentOutcome;
import pe.bcp.login.service.PaymentService;

/**
 * Capa HTTP de pagos. No contiene lógica de negocio.
 */
@Path("/api/v1/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private final PaymentService paymentService;

    @Inject
    public PaymentResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @POST
    public RestResponse<PaymentResponse> create(
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @NotNull(message = "El cuerpo de la solicitud es obligatorio") @Valid CreatePaymentRequest request) {
        CreatePaymentOutcome outcome = paymentService.createPayment(idempotencyKey, request.toCommand());
        PaymentResponse body = PaymentResponse.from(outcome.payment());
        if (outcome.replayed()) {
            return RestResponse.ok(body);
        }
        return RestResponse.status(Response.Status.CREATED, body);
    }

    @GET
    @Path("/{paymentId}")
    public PaymentResponse get(@PathParam("paymentId") String paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
```

```java
// ErrorMappers.java
package pe.bcp.login.resource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.bcp.login.dto.ErrorResponse;
import pe.bcp.login.service.DomainException;
import pe.bcp.login.service.ErrorCode;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce excepciones al formato de error del BFF sin exponer trazas, SQL ni datos sensibles.
 * El detalle técnico solo se registra en el log junto con el traceId.
 */
public class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapDomain(DomainException e) {
        String traceId = newTraceId();
        LOG.infof("traceId=%s code=%s", traceId, e.getCode());
        ErrorResponse body = new ErrorResponse(e.getCode().name(), e.getMessage(), e.isRetryable(), traceId);
        return RestResponse.status(e.getCode().httpStatus(), body);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapValidation(ConstraintViolationException e) {
        String traceId = newTraceId();
        String detail = e.getConstraintViolations().stream()
                .map(ErrorMappers::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        LOG.infof("traceId=%s validación fallida", traceId);
        ErrorResponse body = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                detail.isEmpty() ? "La solicitud no es válida." : detail,
                false,
                traceId);
        return RestResponse.status(400, body);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapWebApplication(WebApplicationException e) {
        String traceId = newTraceId();
        int status = e.getResponse() == null ? 500 : e.getResponse().getStatus();
        if (status >= 500) {
            LOG.errorf(e, "traceId=%s error HTTP interno", traceId);
            return RestResponse.status(500, internal(traceId));
        }
        LOG.infof("traceId=%s solicitud rechazada status=%d", traceId, status);
        String code;
        String message;
        if (status == 404) {
            code = "RESOURCE_NOT_FOUND";
            message = "El recurso solicitado no existe.";
        } else if (status == 400) {
            code = ErrorCode.VALIDATION_ERROR.name();
            message = "La solicitud está mal formada.";
        } else {
            code = "REQUEST_NOT_ACCEPTED";
            message = "La solicitud no pudo ser aceptada.";
        }
        return RestResponse.status(status, new ErrorResponse(code, message, false, traceId));
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapUnexpected(Throwable e) {
        String traceId = newTraceId();
        LOG.errorf(e, "traceId=%s error inesperado", traceId);
        return RestResponse.status(500, internal(traceId));
    }

    private static ErrorResponse internal(String traceId) {
        return new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(),
                "Ocurrió un error interno. Intenta nuevamente más tarde.",
                true,
                traceId);
    }

    private static String describe(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int index = path.lastIndexOf('.');
        String field = index >= 0 ? path.substring(index + 1) : path;
        return field + ": " + violation.getMessage();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
```