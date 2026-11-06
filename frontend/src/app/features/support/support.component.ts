import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SupportService } from '../../core/services/support.service';
import { PaymentsService } from '../../core/services/payments.service';
import { ApiError, Reversal, SupportCase, SupportResolution } from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';

const ELIGIBLE_ACTION_LABELS: Record<string, string> = {
  INVESTIGATE: 'Elegible para investigación',
  RETRY_ELIGIBLE: 'Elegible para reintento',
  DISPUTE_NOT_ELIGIBLE: 'No elegible para disputa (transferencia ya ejecutada)'
};

/**
 * Panel de soporte (Historia: manejo de fallas de pago y reversos).
 *
 * Criterios cubiertos:
 * 1-2. Casos listados con motivo legible y elegibilidad (reintento/
 *      investigación/disputa).
 * 3. El mensaje de "no elegible para disputa" diferencia una transferencia ya
 *    ejecutada de una falla técnica.
 * 4. Resolver un caso registra acción + nota, con referencia de transacción.
 *
 * También expone reversos: crear reverso de un pago exitoso y consultar el
 * estado de un reverso existente.
 */
@Component({
  selector: 'app-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './support.component.html',
  styleUrl: './support.component.css'
})
export class SupportComponent {
  private readonly supportService = inject(SupportService);
  private readonly paymentsService = inject(PaymentsService);

  readonly eligibleActionLabels = ELIGIBLE_ACTION_LABELS;
  readonly cases = signal<SupportCase[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  resolutionByCase: Record<string, SupportResolution> = {};
  noteByCase: Record<string, string> = {};
  readonly resolvingCaseId = signal<string | null>(null);

  reversalPaymentId = '';
  readonly reversalResult = signal<Reversal | null>(null);
  reversalLookupId = '';
  readonly reversalLookupResult = signal<Reversal | null>(null);
  readonly reversalLoading = signal(false);

  constructor() {
    this.loadCases();
  }

  loadCases(): void {
    this.loading.set(true);
    this.error.set(null);
    this.supportService.getCases().subscribe({
      next: (cases) => {
        this.cases.set(cases);
        cases.forEach((c) => {
          this.resolutionByCase[c.caseId] ??= 'RETRY';
          this.noteByCase[c.caseId] ??= '';
        });
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  resolveCase(caseItem: SupportCase): void {
    this.resolvingCaseId.set(caseItem.caseId);
    this.supportService
      .resolveCase(caseItem.caseId, {
        resolution: this.resolutionByCase[caseItem.caseId],
        note: this.noteByCase[caseItem.caseId] || 'Resuelto desde el panel de soporte.'
      })
      .subscribe({
        next: (updated) => {
          this.cases.update((list) => list.map((c) => (c.caseId === updated.caseId ? updated : c)));
          this.resolvingCaseId.set(null);
        },
        error: (error: ApiError) => {
          this.error.set(friendlyErrorMessage(error));
          this.resolvingCaseId.set(null);
        }
      });
  }

  createReversal(): void {
    if (!this.reversalPaymentId.trim()) return;
    this.reversalLoading.set(true);
    this.error.set(null);
    this.paymentsService.createReversal(this.reversalPaymentId.trim()).subscribe({
      next: (reversal) => {
        this.reversalResult.set(reversal);
        this.reversalLoading.set(false);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.reversalLoading.set(false);
      }
    });
  }

  lookupReversal(): void {
    if (!this.reversalLookupId.trim()) return;
    this.reversalLoading.set(true);
    this.error.set(null);
    this.paymentsService.getReversal(this.reversalLookupId.trim()).subscribe({
      next: (reversal) => {
        this.reversalLookupResult.set(reversal);
        this.reversalLoading.set(false);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.reversalLoading.set(false);
      }
    });
  }
}
