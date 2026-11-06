import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Wallet } from '../models/wallet.models';

/** Cliente HTTP de consulta de wallet (ver CONTRACT.md sección 2). */
@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly baseUrl = `${environment.apiBaseUrl}/wallets`;

  constructor(private readonly http: HttpClient) {}

  getWallet(walletId: string): Observable<Wallet> {
    return this.http.get<Wallet>(`${this.baseUrl}/${walletId}`);
  }
}
