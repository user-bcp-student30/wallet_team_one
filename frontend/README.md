# Wallet Perú MVP — Frontend (Angular)

SPA Angular standalone (sin NgModules) que consume el BFF descrito en
`CONTRACT.md` (Quarkus, `http://localhost:8080/api/v1`). Cubre las 6 historias de
usuario del MVP: onboarding, confirmación de pago de bajo monto, idempotencia,
visibilidad de estado QR para comercios, fallas/reversos y soporte, y
conciliación operativa.

## Requisitos

- Node.js 18+ (probado con Node 26 / npm 11).
- Angular CLI 17 (se invoca vía `npx` si no está instalado globalmente).

## Instalación y ejecución local

```powershell
cd frontend
npm install
npm start
```

- `npm start` ejecuta `ng serve` y levanta la app en **http://localhost:4200**.
- La SPA llama al backend en `http://localhost:8080/api/v1` (configurado en
  `src/environments/environment.ts`, clave `apiBaseUrl`). Para apuntar a otro
  backend, edita ese archivo (o `environment.prod.ts` para builds de
  producción) y reinicia `ng serve`.
- El backend (BFF) debe estar corriendo por separado para que las pantallas
  funcionen end-to-end; sin él, la SPA compila e inicia igualmente, pero las
  peticiones HTTP fallarán con un error "No se pudo conectar con el servidor"
  mostrado en el banner de notificaciones.

## Scripts disponibles

| Comando                        | Descripción                                         |
|---------------------------------|------------------------------------------------------|
| `npm start`                     | `ng serve` en modo desarrollo, puerto 4200.           |
| `npm run build`                 | Build de producción en `dist/frontend`.               |
| `npm test -- --watch=false --browsers=ChromeHeadless` | Ejecuta los specs Jasmine/Karma una sola vez. |

## Estructura principal

```
src/app/
  core/
    models/        # Interfaces TS que reflejan el contrato HTTP del BFF
    services/       # Clientes HttpClient por dominio (onboarding, pagos, wallet,
                     # comercio/QR, soporte, operaciones) + NotificationService
    interceptors/   # Interceptor funcional de errores HTTP (errorInterceptor)
    utils/          # Generador/tracker de Idempotency-Key, mapeador de errores
  shared/
    nav-bar/        # Barra superior de navegación
    notifications/  # Banner global de notificaciones (éxito/error/info)
  features/
    home/           # Resumen del MVP (basado en la épica)
    onboarding/      # Wizard de 4 pasos (IDENTITY/DOCUMENT/REVIEW/ACTIVATION)
    payments/        # Alta de pago de bajo monto + historial
    merchant/        # Dashboard de comercio (QR + estado de pagos)
    support/         # Casos de soporte + reversos
    operations/      # Conciliación, incidencias, ledger, settlements
  app.routes.ts, app.config.ts, app.component.ts
src/environments/     # apiBaseUrl configurable por entorno
```

## Rutas de la aplicación

| Ruta                | Pantalla                                              |
|----------------------|--------------------------------------------------------|
| `/`                  | Inicio — resumen del MVP y accesos a cada sección.      |
| `/onboarding`        | Wizard de activación de wallet.                         |
| `/pagos/nuevo`       | Confirmación de pago de bajo monto (con idempotencia).  |
| `/pagos/historial`   | Historial de pagos del payer.                           |
| `/comercio`          | Dashboard de comercio: generación de QR y estado.       |
| `/soporte`           | Casos de soporte y reversos.                             |
| `/operaciones`       | Conciliación diaria, incidencias, ledger y settlements.  |

## Manejo de errores

Todas las peticiones HTTP pasan por `errorInterceptor`
(`src/app/core/interceptors/error.interceptor.ts`), que traduce cualquier
`HttpErrorResponse` al formato unificado del BFF (`{code,message,retryable,traceId}`),
lo muestra en el banner global de notificaciones y lo relanza ya normalizado para
que cada pantalla pueda mostrar también un mensaje inline con
`friendlyErrorMessage`.

## Pruebas unitarias

Specs Jasmine/Karma (esqueleto por defecto de Angular CLI) para:

- `core/services/payments.service.spec.ts` — creación de pagos (incluye envío del
  header `Idempotency-Key`) y manejo de respuesta duplicada.
- `core/utils/idempotency-key.spec.ts` — generador de claves y lógica de
  reutilización/regeneración del `IdempotencyKeyTracker`.
- `core/utils/api-error.mapper.spec.ts` — mapeo de errores HTTP al formato
  unificado y mensajes amigables.

```powershell
npm test -- --watch=false --browsers=ChromeHeadless
```

## Desviaciones respecto a CONTRACT.md

- El contrato no especifica el shape exacto de `LedgerEntry`/`Settlement` más
  allá de "proxy normalizado del mock"; se tomaron los campos del
  `bcp-wallet-mock-openapi.yaml` (`LedgerEntry`, `ProviderPayment`) como
  referencia para los modelos TypeScript de la pantalla de Operaciones.
- El límite de bajo monto (S/ 500.00 / 50000 céntimos) se replica en el
  frontend como validación temprana (antes de enviar la solicitud), tal como
  pide la épica, además de la validación que hace el backend.
- `channel` en incidencias de conciliación se deja como campo de texto libre en
  el filtro, ya que el contrato documenta que hoy solo existe el valor
  constante `"wallet-mock"`.
