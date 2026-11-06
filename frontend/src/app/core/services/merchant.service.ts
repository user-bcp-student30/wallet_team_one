import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateQrRequest,
  MerchantPayment,
  MerchantPaymentQuery,
  MerchantQr,
  ResolvedQr
} from '../models/wallet.models';

/** Cliente HTTP del dashboard de comercio / QR (ver CONTRACT.md sección 4). */
@Injectable({ providedIn: 'root' })
export class MerchantService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  createQr(merchantId: string, request: CreateQrRequest): Observable<MerchantQr> {
    return this.http.post<MerchantQr>(`${this.baseUrl}/merchants/${merchantId}/qr`, request);
  }

  resolveQr(payload: string): Observable<ResolvedQr> {
    return this.http.post<ResolvedQr>(`${this.baseUrl}/qr/resolve`, { payload });
  }

  getPayments(merchantId: string, query: MerchantPaymentQuery): Observable<MerchantPayment[]> {
    let params = new HttpParams();
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.reference) params = params.set('reference', query.reference);
    return this.http.get<MerchantPayment[]>(`${this.baseUrl}/merchants/${merchantId}/payments`, { params });
  }

  refreshPaymentStatus(merchantId: string, paymentId: string): Observable<MerchantPayment> {
    return this.http.post<MerchantPayment>(
      `${this.baseUrl}/merchants/${merchantId}/payments/${paymentId}/refresh`,
      {}
    );
  }
}
