import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OnboardingService } from '../../core/services/onboarding.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  ApiError,
  OnboardingSession,
  OnboardingStepId,
  ReviewScenario
} from '../../core/models/wallet.models';
import { friendlyErrorMessage } from '../../core/utils/api-error.mapper';

const STEP_LABELS: Record<OnboardingStepId, string> = {
  IDENTITY: 'Identidad',
  DOCUMENT: 'Documento',
  REVIEW: 'Revisión',
  ACTIVATION: 'Activación'
};

/**
 * Wizard de onboarding de 4 pasos (IDENTITY → DOCUMENT → REVIEW → ACTIVATION).
 *
 * Cubre los criterios de aceptación de "Registro y activación del MVP":
 * 1. Muestra la secuencia de pasos y el paso actual en todo momento.
 * 2. Bloquea el avance con mensaje accionable si hay datos inválidos.
 * 3. Al finalizar con éxito, la wallet queda activa con acceso a "Pagar".
 * 4. Los eventos de inicio/fin de paso quedan registrados por el backend.
 */
@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.css'
})
export class OnboardingComponent implements OnInit {
  private readonly onboardingService = inject(OnboardingService);
  private readonly notifications = inject(NotificationService);

  readonly stepLabels = STEP_LABELS;
  readonly session = signal<OnboardingSession | null>(null);
  readonly loading = signal(false);
  readonly fieldError = signal<string | null>(null);

  // Estado de formularios
  fullName = '';
  documentNumber = 'TEST-001';
  documentAccepted = false;
  scenario: ReviewScenario = 'APPROVED';
  manualDecision: 'ACTIVE' | 'REJECTED' = 'ACTIVE';

  ngOnInit(): void {
    this.startSession();
  }

  startSession(): void {
    this.loading.set(true);
    this.fieldError.set(null);
    this.onboardingService.startSession().subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.fieldError.set(friendlyErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  submitIdentity(): void {
    const current = this.session();
    if (!current) return;
    this.fieldError.set(null);

    if (!this.fullName.trim()) {
      this.fieldError.set('Ingresa un nombre completo para continuar.');
      return;
    }
    if (!/^TEST-/.test(this.documentNumber)) {
      this.fieldError.set('El documento debe iniciar con el prefijo "TEST-" (ej. TEST-001).');
      return;
    }

    this.loading.set(true);
    this.onboardingService
      .submitIdentityStep(current.onboardingId, {
        fullName: this.fullName.trim(),
        documentType: 'TEST',
        documentNumber: this.documentNumber
      })
      .subscribe({
        next: (session) => {
          this.session.set(session);
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.fieldError.set(friendlyErrorMessage(error));
          this.loading.set(false);
        }
      });
  }

  submitDocument(): void {
    const current = this.session();
    if (!current) return;
    this.fieldError.set(null);

    if (!this.documentAccepted) {
      this.fieldError.set('Debes aceptar la carga del documento ficticio para continuar.');
      return;
    }

    this.loading.set(true);
    this.onboardingService.submitDocumentStep(current.onboardingId, { accept: true }).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.fieldError.set(friendlyErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  submitReview(): void {
    const current = this.session();
    if (!current) return;
    this.fieldError.set(null);
    this.loading.set(true);
    this.onboardingService.submitReviewStep(current.onboardingId, { scenario: this.scenario }).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.fieldError.set(friendlyErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  submitActivation(): void {
    const current = this.session();
    if (!current) return;
    this.fieldError.set(null);
    this.loading.set(true);
    this.onboardingService.submitActivationStep(current.onboardingId).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
        if (session.walletStatus === 'ACTIVE') {
          this.notifications.showSuccess('¡Tu wallet quedó activa! Ya puedes realizar pagos.');
        }
      },
      error: (error: ApiError) => {
        this.fieldError.set(friendlyErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  resolveManualReview(): void {
    const current = this.session();
    if (!current) return;
    this.loading.set(true);
    this.onboardingService
      .resolveManualReview(current.onboardingId, { decision: this.manualDecision })
      .subscribe({
        next: (session) => {
          this.session.set(session);
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.fieldError.set(friendlyErrorMessage(error));
          this.loading.set(false);
        }
      });
  }

  isCurrent(stepId: OnboardingStepId): boolean {
    return this.session()?.currentStep === stepId;
  }
}
