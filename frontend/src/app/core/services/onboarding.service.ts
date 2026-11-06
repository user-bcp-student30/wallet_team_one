import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  IdentityStepPayload,
  DocumentStepPayload,
  ManualReviewDecisionPayload,
  OnboardingAnalytics,
  OnboardingSession,
  ReviewStepPayload
} from '../models/wallet.models';

/** Cliente HTTP del flujo de onboarding (ver CONTRACT.md sección 1). */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly baseUrl = `${environment.apiBaseUrl}/onboarding`;

  constructor(private readonly http: HttpClient) {}

  startSession(): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(`${this.baseUrl}/sessions`, {});
  }

  getSession(onboardingId: string): Observable<OnboardingSession> {
    return this.http.get<OnboardingSession>(`${this.baseUrl}/sessions/${onboardingId}`);
  }

  submitIdentityStep(onboardingId: string, payload: IdentityStepPayload): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(`${this.baseUrl}/sessions/${onboardingId}/steps`, payload);
  }

  submitDocumentStep(onboardingId: string, payload: DocumentStepPayload): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(`${this.baseUrl}/sessions/${onboardingId}/steps`, payload);
  }

  submitReviewStep(onboardingId: string, payload: ReviewStepPayload): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(`${this.baseUrl}/sessions/${onboardingId}/steps`, payload);
  }

  submitActivationStep(onboardingId: string): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(`${this.baseUrl}/sessions/${onboardingId}/steps`, {});
  }

  resolveManualReview(onboardingId: string, payload: ManualReviewDecisionPayload): Observable<OnboardingSession> {
    return this.http.post<OnboardingSession>(
      `${this.baseUrl}/sessions/${onboardingId}/manual-review-decision`,
      payload
    );
  }

  getAnalytics(): Observable<OnboardingAnalytics> {
    return this.http.get<OnboardingAnalytics>(`${this.baseUrl}/analytics`);
  }

  registerAcquisitionSpend(month: string, amountPen: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/analytics/spend`, { month, amountPen });
  }
}
