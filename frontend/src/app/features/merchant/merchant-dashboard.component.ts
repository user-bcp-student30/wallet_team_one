import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MerchantService } from '../../core/services/merchant.service';
import { ApiError, MerchantPayment, MerchantQr } from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';

const MERCHANT_ID = 'merchant-demo';

/**
 * Dashboard de comercio (Historia: visibilidad del estado de pagos QR).
 *
 * Criterios cubiertos:
 * 1-2. Cada intento de pago QR se lista con referencia única y estado dentro
 *      del conjunto habilitado (PENDING/SUCCEEDED/FAILED/REVERSED).
 * 3. Botón "Actualizar estado" para refrescar sin esperar un webhook real.
 * 4. Filtro por fecha y por referencia de transacción.
 */
@Component({
  selector: 'app-merchant-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './merchant-dashboard.component.html',
  styleUrl: './merchant-dashboard.component.css'
})
export class MerchantDashboardComponent {
  private readonly merchantService = inject(MerchantService);

  readonly merchantId = MERCHANT_ID;
  readonly qr = signal<MerchantQr | null>(null);
  readonly payments = signal<MerchantPayment[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly loaded = signal(false);
  readonly refreshingId = signal<string | null>(null);

  qrAmountSoles = '25.00';
  expiresInSeconds = 300;

  reference = '';
  from = '';
  to = '';

  constructor() {
    this.searchPayments();
  }

  generateQr(): void {
    this.error.set(null);
    const amountCents = this.qrAmountSoles.trim()
      ? Math.round(Number(this.qrAmountSoles.replace(',', '.')) * 100)
      : null;
    this.merchantService
      .createQr(this.merchantId, { amountCents, expiresInSeconds: this.expiresInSeconds })
      .subscribe({
        next: (qr) => this.qr.set(qr),
        error: (error: ApiError) => this.error.set(friendlyErrorMessage(error))
      });
  }

  searchPayments(): void {
    this.loading.set(true);
    this.error.set(null);
    this.merchantService
      .getPayments(this.merchantId, {
        from: this.from || undefined,
        to: this.to || undefined,
        reference: this.reference || undefined
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

  refreshStatus(paymentId: string): void {
    this.refreshingId.set(paymentId);
    this.merchantService.refreshPaymentStatus(this.merchantId, paymentId).subscribe({
      next: (updated) => {
        this.payments.update((list) => list.map((p) => (p.paymentId === updated.paymentId ? updated : p)));
        this.refreshingId.set(null);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.refreshingId.set(null);
      }
    });
  }
}
