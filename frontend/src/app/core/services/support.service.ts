import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResolveSupportCaseRequest, SupportCase } from '../models/wallet.models';

/** Cliente HTTP del panel de soporte (ver CONTRACT.md sección 5). */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly baseUrl = `${environment.apiBaseUrl}/support`;

  constructor(private readonly http: HttpClient) {}

  getCases(): Observable<SupportCase[]> {
    return this.http.get<SupportCase[]>(`${this.baseUrl}/cases`);
  }

  resolveCase(caseId: string, request: ResolveSupportCaseRequest): Observable<SupportCase> {
    return this.http.post<SupportCase>(`${this.baseUrl}/cases/${caseId}/resolve`, request);
  }
}
