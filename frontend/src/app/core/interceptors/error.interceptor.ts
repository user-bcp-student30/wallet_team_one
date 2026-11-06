import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../services/notification.service';
import { mapHttpErrorToApiError } from '../utils/api-error.mapper';

/**
 * Interceptor funcional centralizado de errores HTTP.
 *
 * Traduce cualquier `HttpErrorResponse` al formato unificado del BFF
 * (`{code,message,retryable,traceId}`), lo muestra como notificación global y
 * relanza ese `ApiError` ya normalizado (en vez del error crudo de Angular) para
 * que los componentes puedan mostrar mensajes inline sin volver a mapearlo.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const apiError = mapHttpErrorToApiError(error);
        notifications.showError(apiError);
        return throwError(() => apiError);
      }
      return throwError(() => error);
    })
  );
};
