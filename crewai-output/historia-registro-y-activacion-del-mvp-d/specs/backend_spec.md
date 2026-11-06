# backend_spec.md

> **Estado de verificación:** no he compilado ni ejecutado nada. Todo lo que dice "compila", "funciona" o "es seguro" queda como **NO EJECUTADO**.

## 1) Resumen de la implementación

**Qué revisé antes de escribir código**
- Solo tuve el texto del Issue y la parte visible del contrato de Arquitectura. Ese contrato llega truncado después de `E1 … 201 Created`.
- **No tuve acceso al repositorio**, así que no pude comprobar código existente. No sé cómo es hoy `POST /kyc/verifications`.
- **No recibí el modelo de Datos.** No redefino tablas ni genero migraciones. El repositorio usa nombres de tabla y columna **supuestos** (sección 6), que Datos debe confirmar o corregir.

**Qué implementé (solo lo que el contrato visible permite)**
- **E1 `POST /onboarding/sessions`:** inicia o reanuda el onboarding.
  - Si el usuario tiene una sesión `IN_PROGRESS`, la devuelve sin emitir un nuevo evento de inicio.
  - Si no la tiene, crea una sesión en el paso 1 y emite `onboarding_started`.
  - La respuesta sigue el "Modelo de progreso" (§3.2 del contrato).
- **Entradas y validaciones:**
  - El `userId` sale del token, nunca del body.
  - `Idempotency-Key` es obligatorio y debe ser un UUID.
  - `acquisitionChannel` es opcional, con un máximo de 50 caracteres.
  - `X-Correlation-Id` es opcional; si falta o es inválido, el servidor genera uno.
- **Errores:** 400 por validación, 401 sin token o con token inválido, 500 genérico sin detalles internos.
- **Capas:** `resource` (HTTP), `service` (negocio), `repository` (JDBC con `PreparedStatement`), `dto` y `model`.

**Qué NO implementé**

El contrato de los demás endpoints está truncado en mi contexto. No inventé rutas, métodos ni códigos de estado para:
- consultar el progreso;
- enviar un paso (que debería invocar `POST /kyc/verifications`);
- completar el onboarding y activar la wallet;
- el reporte mensual.

Quedan **pendientes** hasta recibir el contrato completo (ver la trazabilidad en la sección 4).

## 2) Clases y responsabilidades

| Paquete | Clase | Responsabilidad |
|---|---|---|
| `pe.bcp.login.resource` | `OnboardingResource` | Endpoint E1. Extrae el `userId` del token, valida los headers, delega al servicio y arma el `Response` (201/200). |
| `pe.bcp.login.resource` | `CorrelationIds` | Resuelve o genera el `X-Correlation-Id` con sanitización. |
| `pe.bcp.login.resource` | `ValidationExceptionMapper`, `ConstraintViolationExceptionMapper`, `StorageExceptionMapper` | Traducen excepciones a `ErrorResponse` con 400/400/500 sin trazas ni SQL. |
| `pe.bcp.login.service` | `OnboardingService` | Lógica de E1: reanudar o crear, resolver la carrera de duplicados y emitir el evento. |
| `pe.bcp.login.service` | `OnboardingStepCatalog` | Catálogo provisional de pasos (supuesto pendiente de Cumplimiento y Producto). |
| `pe.bcp.login.service` | `OnboardingProgressAssembler` | Convierte una sesión en el modelo de progreso (estado de cada paso, `nextAction`). |
| `pe.bcp.login.service` | `StartResult` | Resultado del caso de uso: sesión y si fue creada. |
| `pe.bcp.login.service` | `OnboardingEvent`, `OnboardingEventPublisher`, `CdiOnboardingEventPublisher` | Evento analítico y su publicación. |
| `pe.bcp.login.repository` | `OnboardingSessionRepository` | Acceso a datos por JDBC con consultas parametrizadas. |
| `pe.bcp.login.model` | `OnboardingSession`, `OnboardingStatus`, `WalletStatus`, `StepDefinition` | Modelo de dominio. |
| `pe.bcp.login.dto` | `StartOnboardingRequest`, `OnboardingProgressResponse`, `ErrorResponse` | DTOs de entrada y salida. |
| `pe.bcp.login.exception` | `OnboardingValidationException`, `DuplicateSessionException`, `StorageException` | Errores de negocio y de persistencia. |

### Código Java

```java
// OnboardingStatus.java
package pe.bcp.login.model;

public enum OnboardingStatus {
    IN_PROGRESS,
    COMPLETED,
    CLOSED_INCOMPLETE
}
```

```java
// WalletStatus.java
package pe.bcp.login.model;

public enum WalletStatus {
    PENDING,
    ACTIVE
}
```

```java
// StepDefinition.java
package pe.bcp.login.model;

public record StepDefinition(String stepId, int order, String label, boolean required) {
}
```

```java
// OnboardingSession.java
package pe.bcp.login.model;

import java.time.Instant;
import java.util.UUID;

public record OnboardingSession(
        UUID id,
        UUID userId,
        OnboardingStatus status,
        int currentStepIndex,
        int version,
        WalletStatus walletStatus,
        String acquisitionChannel,
        Instant startedAt,
        Instant updatedAt) {
}
```

```java
// StartOnboardingRequest.java
package pe.bcp.login.dto;

import jakarta.validation.constraints.Size;

public class StartOnboardingRequest {

    @Size(max = 50, message = "El canal de adquisición no puede superar 50 caracteres.")
    public String acquisitionChannel;
}
```

```java
// OnboardingProgressResponse.java
package pe.bcp.login.dto;

import java.util.List;
import java.util.UUID;

public record OnboardingProgressResponse(
        UUID sessionId,
        String status,
        int totalSteps,
        int currentStepIndex,
        String currentStepId,
        List<StepView> steps,
        int version,
        String walletStatus,
        NextAction nextAction) {

    public record StepView(String stepId, int order, String label, boolean required, String status) {
    }

    public record NextAction(String type, String target) {
    }
}
```

```java
// ErrorResponse.java
package pe.bcp.login.dto;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
```

```java
// OnboardingValidationException.java
package pe.bcp.login.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class OnboardingValidationException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public OnboardingValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
```

```java
// DuplicateSessionException.java
package pe.bcp.login.exception;

public class DuplicateSessionException extends RuntimeException {

    public DuplicateSessionException(String message) {
        super(message);
    }
}
```

```java
// StorageException.java
package pe.bcp.login.exception;

public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
```

```java
// OnboardingSessionRepository.java
package pe.bcp.login.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.bcp.login.exception.DuplicateSessionException;
import pe.bcp.login.exception.StorageException;
import pe.bcp.login.model.OnboardingSession;
import pe.bcp.login.model.OnboardingStatus;
import pe.bcp.login.model.WalletStatus;

/**
 * Acceso a datos con JDBC y consultas parametrizadas.
 * SUPUESTO: tabla onboarding_session (PostgreSQL) pendiente de confirmar con Datos.
 * Columnas: id, user_id, status, current_step_index, version, wallet_status,
 * acquisition_channel, started_at, updated_at.
 * Se espera un índice único parcial (user_id) WHERE status = 'IN_PROGRESS'.
 */
@ApplicationScoped
public class OnboardingSessionRepository {

    private static final Logger LOG = Logger.getLogger(OnboardingSessionRepository.class);
    private static final String UNIQUE_VIOLATION = "23505";

    private static final String SELECT_IN_PROGRESS =
            "SELECT id, user_id, status, current_step_index, version, wallet_status, "
                    + "acquisition_channel, started_at, updated_at "
                    + "FROM onboarding_session WHERE user_id = ? AND status = ?";

    private static final String INSERT =
            "INSERT INTO onboarding_session (id, user_id, status, current_step_index, version, "
                    + "wallet_status, acquisition_channel, started_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Inject
    DataSource dataSource;

    public Optional<OnboardingSession> findInProgressByUserId(UUID userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_IN_PROGRESS)) {
            statement.setObject(1, userId);
            statement.setString(2, OnboardingStatus.IN_PROGRESS.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            LOG.error("Fallo al consultar la sesión de onboarding en curso", e);
            throw new StorageException("Error de persistencia", e);
        }
    }

    public void insert(OnboardingSession session) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, session.id());
            statement.setObject(2, session.userId());
            statement.setString(3, session.status().name());
            statement.setInt(4, session.currentStepIndex());
            statement.setInt(5, session.version());
            statement.setString(6, session.walletStatus().name());
            statement.setString(7, session.acquisitionChannel());
            statement.setObject(8, toOffset(session.startedAt()));
            statement.setObject(9, toOffset(session.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new DuplicateSessionException("Ya existe una sesión en curso para el usuario");
            }
            LOG.error("Fallo al insertar la sesión de onboarding", e);
            throw new StorageException("Error de persistencia", e);
        }
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OnboardingSession map(ResultSet rs) throws SQLException {
        return new OnboardingSession(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                OnboardingStatus.valueOf(rs.getString("status")),
                rs.getInt("current_step_index"),
                rs.getInt("version"),
                WalletStatus.valueOf(rs.getString("wallet_status")),
                rs.getString("acquisition_channel"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
```

```java
// OnboardingStepCatalog.java
package pe.bcp.login.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import pe.bcp.login.model.StepDefinition;

/**
 * Catálogo PROVISIONAL de pasos. Es un supuesto del contrato, pendiente de Cumplimiento y Producto.
 * Debe migrarse a configuración externa cuando los requisitos de KYC estén definidos.
 */
@ApplicationScoped
public class OnboardingStepCatalog {

    private static final List<StepDefinition> STEPS = List.of(
            new StepDefinition("PERSONAL_DATA", 1, "Datos personales", true),
            new StepDefinition("IDENTITY_DOCUMENT", 2, "Documento de identidad", true),
            new StepDefinition("CONFIRMATION", 3, "Confirmación", true));

    public List<StepDefinition> steps() {
        return STEPS;
    }

    public int totalSteps() {
        return STEPS.size();
    }

    public Optional<StepDefinition> byOrder(int order) {
        return STEPS.stream().filter(s -> s.order() == order).findFirst();
    }
}
```

```java
// OnboardingProgressAssembler.java
package pe.bcp.login.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.bcp.login.dto.OnboardingProgressResponse;
import pe.bcp.login.dto.OnboardingProgressResponse.NextAction;
import pe.bcp.login.dto.OnboardingProgressResponse.StepView;
import pe.bcp.login.model.OnboardingSession;
import pe.bcp.login.model.OnboardingStatus;
import pe.bcp.login.model.StepDefinition;

@ApplicationScoped
public class OnboardingProgressAssembler {

    private static final String TARGET_LOW_AMOUNT_PAYMENTS = "LOW_AMOUNT_PAYMENTS_ENTRY";

    @Inject
    OnboardingStepCatalog catalog;

    public OnboardingProgressResponse toResponse(OnboardingSession session) {
        List<StepView> views = new ArrayList<>();
        for (StepDefinition definition : catalog.steps()) {
            views.add(new StepView(
                    definition.stepId(),
                    definition.order(),
                    definition.label(),
                    definition.required(),
                    stepStatus(session, definition)));
        }

        String currentStepId = null;
        NextAction nextAction = null;
        if (session.status() == OnboardingStatus.IN_PROGRESS) {
            currentStepId = catalog.byOrder(session.currentStepIndex())
                    .map(StepDefinition::stepId)
                    .orElse(null);
            nextAction = new NextAction("CONTINUE_STEP", currentStepId);
        } else if (session.status() == OnboardingStatus.COMPLETED) {
            nextAction = new NextAction("GO_TO_LOW_AMOUNT_PAYMENTS", TARGET_LOW_AMOUNT_PAYMENTS);
        }

        return new OnboardingProgressResponse(
                session.id(),
                session.status().name(),
                catalog.totalSteps(),
                session.currentStepIndex(),
                currentStepId,
                views,
                session.version(),
                session.walletStatus().name(),
                nextAction);
    }

    private static String stepStatus(OnboardingSession session, StepDefinition definition) {
        if (session.status() == OnboardingStatus.COMPLETED
                || definition.order() < session.currentStepIndex()) {
            return "COMPLETED";
        }
        if (session.status() == OnboardingStatus.IN_PROGRESS
                && definition.order() == session.currentStepIndex()) {
            return "CURRENT";
        }
        return "PENDING";
    }
}
```

```java
// StartResult.java
package pe.bcp.login.service;

import pe.bcp.login.model.OnboardingSession;

public record StartResult(OnboardingSession session, boolean created) {
}
```

```java
// OnboardingEvent.java
package pe.bcp.login.service;

import java.time.Instant;
import java.util.UUID;

public record OnboardingEvent(
        UUID eventId,
        String eventType,
        UUID sessionId,
        UUID userId,
        String stepId,
        String acquisitionChannel,
        Instant occurredAt,
        String correlationId) {

    public static final String ONBOARDING_STARTED = "onboarding_started";
}
```

```java
// OnboardingEventPublisher.java
package pe.bcp.login.service;

public interface OnboardingEventPublisher {

    void publish(OnboardingEvent event);
}
```

```java
// CdiOnboardingEventPublisher.java
package pe.bcp.login.service;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Publicador provisional: dispara un evento CDI asíncrono dentro del proceso.
 * El transporte real (broker, outbox, tabla de eventos) está pendiente de definir con Datos (#14).
 */
@ApplicationScoped
public class CdiOnboardingEventPublisher implements OnboardingEventPublisher {

    private static final Logger LOG = Logger.getLogger(CdiOnboardingEventPublisher.class);

    @Inject
    Event<OnboardingEvent> cdiEvent;

    @Override
    public void publish(OnboardingEvent event) {
        cdiEvent.fireAsync(event);
        LOG.infov("Evento {0} emitido para la sesión {1}", event.eventType(), event.sessionId());
    }
}
```

```java
// OnboardingService.java
package pe.bcp.login.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.bcp.login.exception.DuplicateSessionException;
import pe.bcp.login.exception.StorageException;
import pe.bcp.login.model.OnboardingSession;
import pe.bcp.login.model.OnboardingStatus;
import pe.bcp.login.model.StepDefinition;
import pe.bcp.login.model.WalletStatus;
import pe.bcp.login.repository.OnboardingSessionRepository;

@ApplicationScoped
public class OnboardingService {

    private static final Logger LOG = Logger.getLogger(OnboardingService.class);
    private static final int FIRST_STEP = 1;
    private static final int INITIAL_VERSION = 1;

    @Inject
    OnboardingSessionRepository repository;

    @Inject
    OnboardingStepCatalog catalog;

    @Inject
    OnboardingEventPublisher eventPublisher;

    /**
     * Inicia o reanuda el onboarding del usuario (E1).
     * Reanudar es idempotente por naturaleza: devuelve la sesión IN_PROGRESS existente sin evento nuevo.
     * La Idempotency-Key se valida en la capa HTTP; su persistencia queda pendiente (ver supuestos).
     */
    public StartResult startOrResume(UUID userId, String acquisitionChannel, String correlationId) {
        Optional<OnboardingSession> existing = repository.findInProgressByUserId(userId);
        if (existing.isPresent()) {
            return new StartResult(existing.get(), false);
        }

        OnboardingSession fresh = newSession(userId, normalize(acquisitionChannel));
        try {
            repository.insert(fresh);
        } catch (DuplicateSessionException race) {
            // Otra petición concurrente creó la sesión: se reanuda esa, sin evento de inicio.
            OnboardingSession winner = repository.findInProgressByUserId(userId)
                    .orElseThrow(() -> new StorageException("Estado inconsistente tras conflicto de sesión"));
            return new StartResult(winner, false);
        }

        publishStarted(fresh, correlationId);
        return new StartResult(fresh, true);
    }

    private OnboardingSession newSession(UUID userId, String acquisitionChannel) {
        Instant now = Instant.now();
        return new OnboardingSession(
                UUID.randomUUID(),
                userId,
                OnboardingStatus.IN_PROGRESS,
                FIRST_STEP,
                INITIAL_VERSION,
                WalletStatus.PENDING,
                acquisitionChannel,
                now,
                now);
    }

    private void publishStarted(OnboardingSession session, String correlationId) {
        try {
            String firstStepId = catalog.byOrder(FIRST_STEP).map(StepDefinition::stepId).orElse(null);
            eventPublisher.publish(new OnboardingEvent(
                    UUID.randomUUID(),
                    OnboardingEvent.ONBOARDING_STARTED,
                    session.id(),
                    session.userId(),
                    firstStepId,
                    session.acquisitionChannel(),
                    session.startedAt(),
                    correlationId));
        } catch (RuntimeException e) {
            // La sesión ya está persistida; no se rompe el flujo del usuario por un fallo de analítica.
            // Riesgo: evento perdido. Pendiente: patrón outbox con Datos.
            LOG.errorv(e, "No se pudo emitir onboarding_started para la sesión {0}", session.id());
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

```java
// CorrelationIds.java
package pe.bcp.login.resource;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private CorrelationIds() {
    }

    /** Devuelve el valor recibido si es seguro; en caso contrario genera uno nuevo. */
    public static String resolve(String headerValue) {
        if (headerValue != null && SAFE.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
```

```java
// OnboardingResource.java
package pe.bcp.login.resource;

import java.util.Map;
import java.util.UUID;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pe.bcp.login.dto.OnboardingProgressResponse;
import pe.bcp.login.dto.StartOnboardingRequest;
import pe.bcp.login.exception.OnboardingValidationException;
import pe.bcp.login.service.OnboardingProgressAssembler;
import pe.bcp.login.service.OnboardingService;
import pe.bcp.login.service.StartResult;

@Path("/onboarding/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class OnboardingResource {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    @Inject
    SecurityIdentity identity;

    @Inject
    OnboardingService service;

    @Inject
    OnboardingProgressAssembler assembler;

    /**
     * E1. Iniciar o reanudar onboarding.
     * 201 si se crea; 200 si se reanuda (SUPUESTO: el contrato truncado no confirma el código de reanudación).
     */
    @POST
    public Response start(
            @HeaderParam(IDEMPOTENCY_HEADER) String idempotencyKey,
            @HeaderParam(CorrelationIds.HEADER) String correlationHeader,
            @Valid StartOnboardingRequest request) {

        String correlationId = CorrelationIds.resolve(correlationHeader);
        UUID userId = currentUserId();
        requireValidIdempotencyKey(idempotencyKey);

        String channel = request == null ? null : request.acquisitionChannel;
        StartResult result = service.startOrResume(userId, channel, correlationId);
        OnboardingProgressResponse body = assembler.toResponse(result.session());

        Response.ResponseBuilder builder = result.created()
                ? Response.status(Response.Status.CREATED)
                : Response.ok();
        return builder.entity(body).header(CorrelationIds.HEADER, correlationId).build();
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(identity.getPrincipal().getName());
        } catch (RuntimeException e) {
            // SUPUESTO: el subject del token es un UUID. Si no lo es, no se puede identificar al usuario.
            throw new NotAuthorizedException("Bearer");
        }
    }

    private static void requireValidIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new OnboardingValidationException(
                    "Falta el encabezado Idempotency-Key.",
                    Map.of(IDEMPOTENCY_HEADER, "Envía un UUID único por intento en el encabezado Idempotency-Key."));
        }
        try {
            UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new OnboardingValidationException(
                    "El encabezado Idempotency-Key no es válido.",
                    Map.of(IDEMPOTENCY_HEADER, "El valor debe ser un UUID válido."));
        }
    }
}
```

```java
// ValidationExceptionMapper.java
package pe.bcp.login.resource;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.bcp.login.dto.ErrorResponse;
import pe.bcp.login.dto.ErrorResponse.FieldError;
import pe.bcp.login.exception.OnboardingValidationException;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<OnboardingValidationException> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(OnboardingValidationException exception) {
        List<FieldError> fields = exception.getFieldErrors().entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue()))
                .toList();
        String correlationId = CorrelationIds.resolve(headers.getHeaderString(CorrelationIds.HEADER));
        ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", exception.getMessage(), correlationId, fields);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .header(CorrelationIds.HEADER, correlationId)
                .entity(body)
                .build();
    }
}
```

```java
// ConstraintViolationExceptionMapper.java
package pe.bcp.login.resource;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.bcp.login.dto.ErrorResponse;
import pe.bcp.login.dto.ErrorResponse.FieldError;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fields.add(new FieldError(lastNode(violation.getPropertyPath()), violation.getMessage()));
        }
        String correlationId = CorrelationIds.resolve(headers.getHeaderString(CorrelationIds.HEADER));
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR",
                "Hay datos inválidos en la solicitud. Corrígelos e inténtalo de nuevo.",
                correlationId,
                fields);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .header(CorrelationIds.HEADER, correlationId)
                .entity(body)
                .build();
    }

    private static String lastNode(Path path) {
        String name = "request";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }
}
```

```java
// StorageExceptionMapper.java
package pe.bcp.login.resource;

import java.util.List;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.bcp.login.dto.ErrorResponse;
import pe.bcp.login.exception.StorageException;

/** Respuesta genérica: nunca expone SQL, trazas ni detalles de la base de datos. */
@Provider
public class StorageExceptionMapper implements ExceptionMapper<StorageException> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(StorageException exception) {
        String correlationId = CorrelationIds.resolve(headers.getHeaderString(CorrelationIds.HEADER));
        ErrorResponse body = new ErrorResponse(
                "INTERNAL_ERROR",
                "No pudimos procesar tu solicitud. Inténtalo nuevamente en unos minutos.",
                correlationId,
                List.of());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .header(CorrelationIds.HEADER, correlationId)
                .entity(body)
                .build();
    }
}
```

## 3) Recorrido de un caso válido y uno de error

**Caso válido: usuario nuevo inicia el onboarding**
1. Llega `POST /onboarding/sessions` con `Authorization: Bearer …`, `Idempotency-Key: <uuid>` y body `{"acquisitionChannel":"referido"}`.
2. Quarkus autentica la petición (`@Authenticated`); sin token válido respondería 401. `@Valid` comprueba el máximo de 50 caracteres.
3. En `OnboardingResource.start`:
   - se resuelve el `X-Correlation-Id`;
   - el `userId` sale del subject del token;
   - se valida que `Idempotency-Key` sea un UUID.
4. `OnboardingService.startOrResume` llama a `OnboardingSessionRepository.findInProgressByUserId` (`SELECT … WHERE user_id = ? AND status = ?`). No hay sesión en curso.
5. El servicio crea una `OnboardingSession` con `IN_PROGRESS`, paso 1, versión 1 y wallet `PENDING`, y la guarda con `repository.insert` (`INSERT` parametrizado).
6. `CdiOnboardingEventPublisher` emite `onboarding_started`, con el timestamp asignado por el servidor.
7. `OnboardingProgressAssembler` arma el modelo de progreso: `PERSONAL_DATA` queda `CURRENT`, los otros dos `PENDING`, y `nextAction = CONTINUE_STEP → PERSONAL_DATA`.
8. Respuesta: `201 Created` con el body de progreso y el header `X-Correlation-Id`.

**Caso de error: falta `Idempotency-Key`**
1. Llega el mismo `POST`, pero sin el header `Idempotency-Key`.
2. `OnboardingResource.requireValidIdempotencyKey` lanza `OnboardingValidationException`, **antes de tocar la base de datos**.
3. `ValidationExceptionMapper` responde `400` con este body:
   ```json
   {"code":"VALIDATION_ERROR","message":"Falta el encabezado Idempotency-Key.","correlationId":"…","fieldErrors":[{"field":"Idempotency-Key","message":"Envía un UUID único por intento en el encabezado Idempotency-Key."}]}
   ```
   No se expone ninguna traza ni dato interno.

## 4) Trazabilidad criterio de aceptación → código

| # | Criterio | Cobertura | Código |
|---|---|---|---|
| 1 | Secuencia de pasos y paso actual siempre visibles | **Parcial.** La respuesta de E1 incluye `steps`, `currentStepIndex`, `currentStepId` y `nextAction`. Falta el endpoint de consulta de progreso y el de avance (contrato truncado). | `OnboardingProgressAssembler.toResponse`, `OnboardingStepCatalog` |
| 2 | Datos faltantes o inválidos bloquean el avance con mensaje accionable | **Parcial.** Solo valida las entradas de E1 (`Idempotency-Key`, `acquisitionChannel`). La validación de los datos de cada paso **NO está cubierta**: falta el contrato del endpoint de envío de paso. | `OnboardingResource.requireValidIdempotencyKey`, `StartOnboardingRequest`, `ValidationExceptionMapper`, `ConstraintViolationExceptionMapper` |
| 3 | Al finalizar, wallet activa y acceso a pagos de bajo monto | **NO cubierto.** Falta el endpoint de completar y el servicio dueño del estado de la wallet. El ensamblador ya mapea `COMPLETED → GO_TO_LOW_AMOUNT_PAYMENTS`, pero nada transiciona la sesión a ese estado. | `OnboardingProgressAssembler` (solo la salida) |
| 4 | Eventos de inicio, fin de cada paso y cierre | **Parcial.** Solo `onboarding_started`. Faltan los eventos de fin de paso y de cierre. | `OnboardingService.publishStarted`, `CdiOnboardingEventPublisher` |
| 5 | Mediana de onboarding de 8 a 3 minutos | **NO cubierto por backend.** Es un resultado de negocio. Backend solo aporta `startedAt` asignado por el servidor y el evento de inicio. El cálculo corresponde a Datos (#14). | `OnboardingSession.startedAt` |
| 6 | Salida analítica de MAU y CAC | **NO cubierto.** Falta el contrato del reporte y la fuente de gasto de adquisición. Backend solo guarda `acquisitionChannel`. | `OnboardingSession.acquisitionChannel` |

**Tareas del Issue:** #12 queda solo parcialmente cubierta, porque falta la integración con `POST /kyc/verifications`. #13 es de Frontend. #14 es de Datos.

## 5) Comandos de verificación (NO EJECUTADO)

Ninguno de estos comandos se ha ejecutado. No hay resultados de compilación ni de pruebas.

```bash
mvn -q clean compile      # NO EJECUTADO
mvn test                  # NO EJECUTADO (no escribí pruebas; quedan pendientes)
mvn quarkus:dev           # NO EJECUTADO
```

Verificación manual propuesta, también **NO EJECUTADA**:
- `POST /onboarding/sessions` sin `Idempotency-Key` debe dar 400.
- Con un body de `acquisitionChannel` de 51 caracteres debe dar 400.
- Sin token debe dar 401.
- La primera llamada válida debe dar 201 y la segunda 200 con el mismo `sessionId`.
- Dos llamadas concurrentes no deben crear dos sesiones.

## 6) Supuestos y pendientes para otros roles

**Para Arquitectura**
- Enviar el contrato completo de los demás endpoints (progreso, envío de paso, completar, reporte) y el formato de errores. Sin eso no los implemento.
- Confirmar el código de estado al reanudar: asumí `200 OK`.
- Confirmar el mecanismo de autenticación: asumí JWT, con el `sub` como UUID del usuario.
- Definir el contrato de `Idempotency-Key`: duración, qué pasa con la misma clave y distinto body (¿409?) y el replay de respuesta. **Hoy solo se valida el formato; no se persiste.**
- Mi `ErrorResponse` (`code`, `message`, `correlationId`, `fieldErrors`) es un supuesto. Debe alinearse con el formato aprobado.
- La estructura de paquetes difiere de lo pedido en el formato de entrega: solo las clases de negocio están en `pe.bcp.login.service`. El resto está en `resource`, `repository`, `dto`, `model` y `exception` bajo `pe.bcp.login`, para respetar la separación de capas. Confirmar si se acepta.

**Para Datos**
- Confirmar o corregir la tabla `onboarding_session` y sus columnas (sección 2, `OnboardingSessionRepository`). No generé migraciones.
- Crear un índice único parcial `(user_id) WHERE status = 'IN_PROGRESS'`. Sin él, la protección contra sesiones duplicadas concurrentes no funciona.
- Definir el transporte de eventos y el patrón outbox. Hoy es solo un evento CDI en memoria, y si falla la publicación el evento se pierde.
- Definir la frontera de eventos que determina la duración del onboarding (pregunta abierta del Issue) y la fuente del gasto de adquisición.

**Para Cumplimiento y Producto**
- El catálogo de pasos es provisional. Los requisitos de KYC siguen sin definirse.

**Para Frontend**
- La ruta real del punto de entrada de pagos la resuelve el frontend a partir de la clave lógica `LOW_AMOUNT_PAYMENTS_ENTRY`.

**Dependencias y configuración de Quarkus (supuestas, no verificadas)**
- Extensiones necesarias: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-agroal`, el driver JDBC de PostgreSQL y `quarkus-smallrye-jwt`.
- Esto asume Quarkus 3.x con `jakarta.*`. No modifiqué ningún `pom.xml`.
- No hay secretos en el código. La conexión a la base de datos y la configuración JWT deben venir de la configuración del entorno.
- Cliente sin `Content-Type`: omití `@Consumes` a propósito para aceptar un body vacío. Debe probarse.
- Un mismo usuario puede obtener 200 en un reintento, aunque el primer intento haya devuelto 201.

**Riesgos conocidos**
- Una excepción no mapeada (distinta de `StorageException`) queda a cargo del manejo por defecto de Quarkus. En producción oculta las trazas, pero debe verificarse.
- Si mis mappers no tuvieran prioridad sobre los de Quarkus para `ConstraintViolationException`, el formato de error 400 sería distinto. Todo esto es **NO EJECUTADO**.