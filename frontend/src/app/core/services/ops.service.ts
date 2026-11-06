import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  IncidentsQuery,
  LedgerEntry,
  LedgerQuery,
  ReconciliationIncident,
  ReconciliationSummary,
  Settlement
} from '../models/wallet.models';

/** Cliente HTTP del panel de operaciones / conciliación (ver CONTRACT.md sección 6). */
@Injectable({ providedIn: 'root' })
export class OperationsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/ops`;

  constructor(private readonly http: HttpClient) {}

  getLedger(query: LedgerQuery): Observable<LedgerEntry[]> {
    let params = new HttpParams();
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.account) params = params.set('account', query.account);
    return this.http.get<LedgerEntry[]>(`${this.baseUrl}/ledger`, { params });
  }

  getSettlements(query: LedgerQuery): Observable<Settlement[]> {
    let params = new HttpParams();
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    return this.http.get<Settlement[]>(`${this.baseUrl}/settlements`, { params });
  }

  runReconciliation(): Observable<ReconciliationSummary> {
    return this.http.post<ReconciliationSummary>(`${this.baseUrl}/reconciliations/run`, {});
  }

  getIncidents(query: IncidentsQuery): Observable<ReconciliationIncident[]> {
    let params = new HttpParams();
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.channel) params = params.set('channel', query.channel);
    return this.http.get<ReconciliationIncident[]>(`${this.baseUrl}/reconciliations/incidents`, { params });
  }

  getSummary(date: string): Observable<ReconciliationSummary> {
    const params = new HttpParams().set('date', date);
    return this.http.get<ReconciliationSummary>(`${this.baseUrl}/reconciliations/summary`, { params });
  }
}
