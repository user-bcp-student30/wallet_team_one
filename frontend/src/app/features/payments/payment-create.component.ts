import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PaymentsService } from '../../core/services/payments.service';
import { NotificationService } from '../../core/services/notification.service';
import { ApiError, Payment, ReviewScenario } from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';
import { IdempotencyKeyTracker } from '../../core/utils/idempotency-key';

const LOW_VALUE_LIMIT_CENTS = 50000;
const DEFAULT_WALLET_ID = 'wallet-demo';

type WizardStage = 'FORM' | 'CONFIRM' | 'RESULT';

/**
 * Formulario de pago de bajo monto (Historia: confirmación de pago de bajo monto
 * + idempotencia).
 *
 * Criterios cubiertos:
 * 1. Muestra destino y monto antes de confirmar (etapa CONFIRM).
 * 2. Un único resultado terminal por intento (etapa RESULT).
 * 3. Si es exitoso, se muestra referencia de transacción.
 * 4. Si falla/expira, ofrece reintentar (misma Idempotency-Key) o volver
 *    (nueva Idempotency-Key si cambia monto/destino) sin duplicar el cobro.
 */
@Component({
  selector: 'app-payment-create',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-create.component.html',
  styleUrl: './payment-create.component.css'
})
export class PaymentCreateComponent {
  private readonly paymentsService = inject(PaymentsService);
  private readonly notifications = inject(NotificationService);
  private readonly idempotencyTracker = new IdempotencyKeyTracker();

  readonly lowValueLimitCents = LOW_VALUE_LIMIT_CENTS;
  readonly stage = signal<WizardStage>('FORM');
  readonly loading = signal(false);
  readonly fieldError = signal<string | null>(null);
  readonly result = signal<Payment | null>(null);
  readonly lastIdempotencyKey = signal<string | null>(null);

  recipient: 'contact-demo' | 'merchant-demo' = 'contact-demo';
  amountSoles = '15.00';
  scenario: ReviewScenario = 'APPROVED';

  get amountCents(): number {
    const parsed = Number(this.amountSoles.replace(',', '.'));
    return Math.round((isNaN(parsed) ? 0 : parsed) * 100);
  }

  goToConfirm(): void {
    this.fieldError.set(null);
    if (this.amountCents <= 0) {
      this.fieldError.set('Ingresa un monto válido mayor a cero.');
      return;
    }
    if (this.amountCents > LOW_VALUE_LIMIT_CENTS) {
      this.fieldError.set(
        `El monto supera el límite de pago de bajo monto (S/ ${(LOW_VALUE_LIMIT_CENTS / 100).toFixed(2)}).`
      );
      return;
    }
    this.stage.set('CONFIRM');
  }

  backToForm(): void {
    this.stage.set('FORM');
  }

  confirmPayment(): void {
    this.loading.set(true);
    this.fieldError.set(null);
    const key = this.idempotencyTracker.keyFor(this.recipient, this.amountCents, null);
    this.lastIdempotencyKey.set(key);

    this.paymentsService
      .createPayment(
        {
          walletId: DEFAULT_WALLET_ID,
          recipient: this.recipient,
          amountCents: this.amountCents,
          scenario: this.scenario,
          qrId: null
        },
        key
      )
      .subscribe({
        next: (payment) => {
          this.result.set(payment);
          this.stage.set('RESULT');
          this.loading.set(false);
          if (payment.status === 'SUCCEEDED') {
            this.notifications.showSuccess(`Pago exitoso. Referencia: ${payment.reference}`);
            this.idempotencyTracker.reset();
          }
        },
        error: (error: ApiError) => {
          this.fieldError.set(friendlyErrorMessage(error));
          this.loading.set(false);
          this.stage.set('CONFIRM');
        }
      });
  }

  /** Reintenta el MISMO intento (misma clave de idempotencia porque no cambió
   * destino/monto) para evitar crear un cobro duplicado. */
  retrySameAttempt(): void {
    this.stage.set('CONFIRM');
    this.result.set(null);
  }

  /** Vuelve al formulario para un intento distinto; al cambiar monto/destino el
   * tracker generará automáticamente una nueva clave de idempotencia. */
  startNewAttempt(): void {
    this.idempotencyTracker.reset();
    this.result.set(null);
    this.lastIdempotencyKey.set(null);
    this.stage.set('FORM');
  }
}
