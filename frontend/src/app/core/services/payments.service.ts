import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreatePaymentRequest, Payment, PaymentHistoryQuery, Reversal } from '../models/wallet.models';

/**
 * Cliente HTTP de pagos payer-to-contact (ver CONTRACT.md sección 3).
 *
 * La clave de idempotencia se envía siempre en el header `Idempotency-Key`; el
 * generador/tracker vive en `core/utils/idempotency-key.ts` y es responsabilidad
 * del componente llamante decidir cuándo reutilizarla o regenerarla.
 */
@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/payments`;

  constructor(private readonly http: HttpClient) {}

  createPayment(request: CreatePaymentRequest, idempotencyKey: string): Observable<Payment> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<Payment>(this.baseUrl, request, { headers });
  }

  getPayment(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/${paymentId}`);
  }

  /** Fuerza una nueva consulta de estado al proveedor para un pago existente
   * (por ejemplo un pago `IN_REVIEW`). */
  resolvePayment(paymentId: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${paymentId}/resolve`, {});
  }

  getHistory(query: PaymentHistoryQuery): Observable<Payment[]> {
    let params = new HttpParams();
    if (query.walletId) params = params.set('walletId', query.walletId);
    if (query.recipient) params = params.set('recipient', query.recipient);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.status) params = params.set('status', query.status);
    return this.http.get<Payment[]>(this.baseUrl, { params });
  }

  getReceipt(paymentId: string): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/${paymentId}/receipt`);
  }

  createReversal(paymentId: string): Observable<Reversal> {
    return this.http.post<Reversal>(`${this.baseUrl}/${paymentId}/reversals`, {});
  }

  getReversal(reversalId: string): Observable<Reversal> {
    return this.http.get<Reversal>(`${environment.apiBaseUrl}/reversals/${reversalId}`);
  }
}
