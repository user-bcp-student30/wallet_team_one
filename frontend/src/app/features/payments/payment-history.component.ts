import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PaymentsService } from '../../core/services/payments.service';
import { ApiError, Payment, PaymentStatus } from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';

const DEFAULT_WALLET_ID = 'wallet-demo';

/** Historial de pagos del payer, con referencia de transacción y estado
 * (criterio #3 de la historia de confirmación de pago). */
@Component({
  selector: 'app-payment-history',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-history.component.html',
  styleUrl: './payment-history.component.css'
})
export class PaymentHistoryComponent {
  private readonly paymentsService = inject(PaymentsService);

  readonly payments = signal<Payment[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly loaded = signal(false);

  recipient = '';
  status: PaymentStatus | '' = '';
  from = '';
  to = '';

  constructor() {
    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.error.set(null);
    this.paymentsService
      .getHistory({
        walletId: DEFAULT_WALLET_ID,
        recipient: this.recipient || undefined,
        from: this.from || undefined,
        to: this.to || undefined,
        status: (this.status || undefined) as PaymentStatus | undefined
      })
      .subscribe({
        next: (payments) => {
          this.payments.set(payments);
          this.loading.set(false);
          this.loaded.set(true);
        },
        error: (error: ApiError) => {
          this.error.set(friendlyErrorMessage(error));
          this.loading.set(false);
          this.loaded.set(true);
        }
      });
  }
}
