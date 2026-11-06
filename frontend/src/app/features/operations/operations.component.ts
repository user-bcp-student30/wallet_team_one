import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OperationsService } from '../../core/services/ops.service';
import {
  ApiError,
  LedgerEntry,
  ReconciliationIncident,
  ReconciliationSummary,
  Settlement
} from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';

/**
 * Panel de operaciones y conciliación (Historia: conciliación operativa).
 *
 * Criterios cubiertos:
 * 1. Compara wallet vs. proveedor al correr la conciliación.
 * 2. Cada incidencia se marca con referencia, tipo de descalce y estado.
 * 3. Filtros de fecha y canal sobre incidencias; filtro de fecha sobre ledger.
 * 4. Resumen diario con totales conciliados/no conciliados/resueltos.
 */
@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.css'
})
export class OperationsComponent {
  private readonly opsService = inject(OperationsService);

  readonly running = signal(false);
  readonly summary = signal<ReconciliationSummary | null>(null);
  readonly error = signal<string | null>(null);

  summaryDate = '';

  incidentsFrom = '';
  incidentsTo = '';
  incidentsChannel = '';
  readonly incidents = signal<ReconciliationIncident[]>([]);
  readonly incidentsLoading = signal(false);

  ledgerFrom = '';
  ledgerTo = '';
  ledgerAccount = '';
  readonly ledger = signal<LedgerEntry[]>([]);
  readonly ledgerLoading = signal(false);

  settlementsFrom = '';
  settlementsTo = '';
  readonly settlements = signal<Settlement[]>([]);
  readonly settlementsLoading = signal(false);

  runReconciliation(): void {
    this.running.set(true);
    this.error.set(null);
    this.opsService.runReconciliation().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.running.set(false);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.running.set(false);
      }
    });
  }

  loadSummaryByDate(): void {
    if (!this.summaryDate) return;
    this.running.set(true);
    this.error.set(null);
    this.opsService.getSummary(this.summaryDate).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.running.set(false);
      },
      error: (error: ApiError) => {
        this.error.set(friendlyErrorMessage(error));
        this.running.set(false);
      }
    });
  }

  searchIncidents(): void {
    this.incidentsLoading.set(true);
    this.error.set(null);
    this.opsService
      .getIncidents({
        from: this.incidentsFrom || undefined,
        to: this.incidentsTo || undefined,
        channel: this.incidentsChannel || undefined
      })
      .subscribe({
        next: (incidents) => {
          this.incidents.set(incidents);
          this.incidentsLoading.set(false);
        },
        error: (error: ApiError) => {
          this.error.set(friendlyErrorMessage(error));
          this.incidentsLoading.set(false);
        }
      });
  }

  searchLedger(): void {
    this.ledgerLoading.set(true);
    this.error.set(null);
    this.opsService
      .getLedger({
        from: this.ledgerFrom || undefined,
        to: this.ledgerTo || undefined,
        account: this.ledgerAccount || undefined
      })
      .subscribe({
        next: (entries) => {
          this.ledger.set(entries);
          this.ledgerLoading.set(false);
        },
        error: (error: ApiError) => {
          this.error.set(friendlyErrorMessage(error));
          this.ledgerLoading.set(false);
        }
      });
  }

  searchSettlements(): void {
    this.settlementsLoading.set(true);
    this.error.set(null);
    this.opsService
      .getSettlements({ from: this.settlementsFrom || undefined, to: this.settlementsTo || undefined })
      .subscribe({
        next: (settlements) => {
          this.settlements.set(settlements);
          this.settlementsLoading.set(false);
        },
        error: (error: ApiError) => {
          this.error.set(friendlyErrorMessage(error));
          this.settlementsLoading.set(false);
        }
      });
  }
}
