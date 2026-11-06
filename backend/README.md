# Wallet Backend (Quarkus BFF)

Backend for Wallet Perú MVP. It is a **BFF (Backend-for-Frontend)** built with
[Quarkus](https://quarkus.io) that exposes `/api/v1/*` for the Angular frontend and
orchestrates calls to the external sandbox mock `bcp-wallet-mock`
(`bcp-wallet-mock-openapi.yaml`). The exact HTTP contract is documented in
`CONTRACT.md` (provided separately to this repo during design).

## Stack

- Java 17 (compiles fine on JDK 21 too, `maven.compiler.release=17`)
- Quarkus 3.8.3
- RESTEasy Reactive + Jackson (JAX-RS resources)
- Quarkus REST Client Reactive + Jackson (consumes the external mock)
- Hibernate ORM with Panache + H2 in-memory (local "journal": idempotency audit,
  first-seen timestamps for payments/QR/ledger/settlements/incidents, onboarding
  events, support actions, daily reconciliation snapshots)
- SmallRye Health (`/q/health`)
- Bean Validation (Hibernate Validator)

## Running locally

### Option A — Dev mode (hot reload)

```powershell
cd backend
mvn quarkus:dev
```

### Option B — Build + run the packaged jar

```powershell
cd backend
mvn -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar
```

The backend listens on **port 8080** by default
(`http://localhost:8080`).

Health check: `GET http://localhost:8080/q/health`

## Environment variables / configuration

| Property                              | Env var           | Default                                                   | Purpose                                   |
|----------------------------------------|--------------------|-------------------------------------------------------------|--------------------------------------------|
| `mock.base-url`                        | `MOCK_BASE_URL`     | `https://bcp-wallet-mock.yefri.workers.dev/groups/1`         | Base URL of the external wallet mock (sandbox Group 1, no auth) |
| `payments.low-value-limit-cents`       | —                   | `50000` (S/ 500.00)                                          | Low-value payment limit validated **before** calling the mock |
| `payments.resolve-retry-window-seconds`| —                   | `15`                                                          | Reserved for future retry/expiration policy (documented assumption) |
| `quarkus.http.port`                    | `QUARKUS_HTTP_PORT` | `8080`                                                        | HTTP port |

Example (PowerShell):

```powershell
$env:MOCK_BASE_URL = "https://bcp-wallet-mock.yefri.workers.dev/groups/1"
mvn quarkus:dev
```

CORS is enabled for `http://localhost:4200` (the Angular dev server) via
`quarkus.http.cors.*` in `application.properties`.

## Running the tests

Tests use `quarkus-junit5` + `rest-assured` and call the **real sandbox mock**
directly (no HTTP mocking framework) — this is the intended approach for this
project since the sandbox (`.../groups/1`) is isolated per team. Each test
generates its own random `Idempotency-Key`/data so test runs don't collide with
each other or with manual testing against the same sandbox.

```powershell
cd backend
mvn test
```

Covered scenarios:
- Low-value payment creation resolved to `SUCCEEDED` in one round trip.
- Payment above the low-value limit rejected **before** calling the mock
  (`LOW_VALUE_LIMIT_EXCEEDED`, 400).
- Idempotency replay: same key + same body → `200`, `duplicate:true`, same `paymentId`.
- Idempotency conflict: same key + different body → `409 IDEMPOTENCY_CONFLICT`.
- Full QR flow: create QR for `merchant-demo`, resolve payload.
- Reversal of a `SUCCEEDED` payment accepted (`PENDING`/`SUCCEEDED`).
- Unified error contract does **not** leak stack traces / internal class names
  / exception messages on a 404.
- Wallet proxy normalizes mock's snake_case fields to camelCase.

## Package layout

```
pe.bcp.wallet
├── client       REST Client interface to the external mock + error mapper
├── config       Startup logging (prints MOCK_BASE_URL on boot)
├── dto
│   ├── api      Request/response DTOs exposed to the frontend (camelCase)
│   └── mock     DTOs mirroring bcp-wallet-mock-openapi.yaml (snake_case where applicable)
├── entity       Panache entities for the local H2 journal
├── exception    AppException / MockApiException + GlobalExceptionMapper (unified error contract)
├── resource     JAX-RS resources (/api/v1/*)
├── service      Business/orchestration logic
└── util         Small helpers (date parsing)
```

## Endpoints implemented (`/api/v1`)

**Onboarding**
- `POST /onboarding/sessions`
- `POST /onboarding/sessions/{onboardingId}/steps`
- `GET  /onboarding/sessions/{onboardingId}`
- `POST /onboarding/sessions/{onboardingId}/manual-review-decision`
- `GET  /onboarding/analytics`
- `POST /onboarding/analytics/spend`

**Wallet**
- `GET /wallets/{walletId}`

**Payments**
- `POST /payments` (requires `Idempotency-Key` header; generates one if missing and
  returns it in the response header)
- `GET  /payments/{id}`
- `GET  /payments?walletId=&recipient=&from=&to=&status=`
- `GET  /payments/{id}/receipt`
- `POST /payments/{id}/resolve`
- `POST /payments/{id}/reversals`
- `GET  /reversals/{id}`

**QR comercio**
- `POST /merchants/{merchantId}/qr`
- `POST /qr/resolve`
- `GET  /merchants/{merchantId}/payments?from=&to=&reference=`
- `POST /merchants/{merchantId}/payments/{paymentId}/refresh`

**Soporte / reversos**
- `GET  /support/cases`
- `POST /support/cases/{caseId}/resolve`

**Operaciones / conciliación**
- `GET  /ops/ledger?from=&to=&account=`
- `GET  /ops/settlements?from=&to=`
- `POST /ops/reconciliations/run`
- `GET  /ops/reconciliations/incidents?from=&to=&channel=`
- `GET  /ops/reconciliations/summary?date=`

**Admin (pruebas locales/demo, no historias de usuario)**
- `POST /admin/reset`
- `POST /admin/payments/{id}/advance-provider`

## Unified error contract

Every error response (BFF validation errors and translated mock errors) has the
shape:

```json
{ "code": "STRING_CODE", "message": "human readable text", "retryable": true, "traceId": "uuid" }
```

`code` mirrors or translates the mock's `error` field, or is a BFF-specific code
(`LOW_VALUE_LIMIT_EXCEEDED`, `VALIDATION_ERROR`, `UPSTREAM_ERROR`,
`ONBOARDING_TERMINAL`, `NOT_IN_REVIEW`...). Internal details (stack traces, SQL,
class names) are **never** included — this is covered by an automated test
(`errorResponseDoesNotLeakInternalDetails`).

## Documented deviations / assumptions (see CONTRACT.md for the full list)

- Low-value limit: 50000 céntimos (S/ 500), configurable via
  `payments.low-value-limit-cents`.
- `GET /payments/{id}` normalizes status directly from the mock (`PENDING`,
  `SUCCEEDED`, `FAILED`, `REVERSED`); it does not re-derive `IN_REVIEW`, which is
  only produced by the explicit `resolve` flow (mock's 202 `escalated:true`).
- Reversal endpoint always answers HTTP 200 with the reversal body
  (`status: PENDING|SUCCEEDED`), rather than mirroring the mock's 202/200
  distinction at the transport level — the state is still visible in the body.
- Support case `resourceType` is derived heuristically from the `resource` id
  prefix (`pay_` ⇒ `PAYMENT`, otherwise `KYC`), since the mock does not expose an
  explicit type field.
- `channel` in reconciliation incidents is a constant `"wallet-mock"` (single
  provider in the mock, as documented in CONTRACT.md).
- Onboarding analytics/spend is a simple manual entry per month (no real
  marketing/attribution system behind it) — MVP simplification, documented in
  CONTRACT.md.
- The idempotency/journal data lives in an in-memory H2 database: **it resets on
  every backend restart** (MVP limitation).
