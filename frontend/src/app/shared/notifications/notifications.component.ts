import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { NotificationService } from '../../core/services/notification.service';

/** Banner global de notificaciones (errores/éxitos) alimentado por el
 * interceptor HTTP centralizado y las pantallas de la aplicación. */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="notifications" *ngIf="notifications.notifications().length">
      <div
        *ngFor="let n of notifications.notifications()"
        class="toast"
        [class.error]="n.kind === 'error'"
        [class.success]="n.kind === 'success'"
        [class.info]="n.kind === 'info'"
      >
        <span>{{ n.message }}</span>
        <small *ngIf="n.traceId">traceId: {{ n.traceId }}</small>
        <button type="button" (click)="notifications.dismiss(n.id)" aria-label="Cerrar">×</button>
      </div>
    </div>
  `,
  styles: [
    `
      .notifications {
        position: fixed;
        top: 0.75rem;
        right: 0.75rem;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        z-index: 1000;
        max-width: 360px;
      }
      .toast {
        display: flex;
        flex-direction: column;
        padding: 0.65rem 0.9rem;
        border-radius: 6px;
        color: #fff;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
        position: relative;
      }
      .toast.error {
        background: #b3261e;
      }
      .toast.success {
        background: #1e6b3c;
      }
      .toast.info {
        background: #1e4e8c;
      }
      .toast small {
        opacity: 0.8;
        font-size: 0.7rem;
      }
      .toast button {
        position: absolute;
        top: 0.25rem;
        right: 0.4rem;
        background: transparent;
        border: none;
        color: #fff;
        cursor: pointer;
        font-size: 1rem;
        line-height: 1;
      }
    `
  ]
})
export class NotificationsComponent {
  protected readonly notifications = inject(NotificationService);
}
