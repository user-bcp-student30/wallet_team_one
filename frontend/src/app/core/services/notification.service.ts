import { Injectable, signal } from '@angular/core';
import { ApiError } from '../models/wallet.models';

/** Notificación simple mostrada en el banner global de la aplicación. */
export interface Notification {
  id: number;
  kind: 'error' | 'success' | 'info';
  message: string;
  traceId?: string;
}

/** Servicio simple de notificaciones en memoria (signal) usado por el
 * interceptor de errores HTTP y por las pantallas para dar feedback global. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly notifications = signal<Notification[]>([]);

  showError(apiError: ApiError): void {
    this.push('error', apiError.message, apiError.traceId);
  }

  showSuccess(message: string): void {
    this.push('success', message);
  }

  showInfo(message: string): void {
    this.push('info', message);
  }

  dismiss(id: number): void {
    this.notifications.update((list) => list.filter((n) => n.id !== id));
  }

  private push(kind: Notification['kind'], message: string, traceId?: string): void {
    const notification: Notification = { id: this.nextId++, kind, message, traceId };
    this.notifications.update((list) => [...list, notification]);
    setTimeout(() => this.dismiss(notification.id), 8000);
  }
}
