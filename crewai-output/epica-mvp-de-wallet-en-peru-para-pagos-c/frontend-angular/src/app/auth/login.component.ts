// login.component.ts

import { ChangeDetectorRef, Component, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginService } from './login.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  loading = false;
  submitted = false;
  apiErrorMessage: string | null = null;
  successMessage: string | null = null;

  @ViewChild('usernameInput')
  private usernameInput?: ElementRef<HTMLInputElement>;

  @ViewChild('passwordInput')
  private passwordInput?: ElementRef<HTMLInputElement>;

  @ViewChild('errorSummary')
  private errorSummary?: ElementRef<HTMLDivElement>;

  constructor(
    private readonly loginService: LoginService,
    private readonly changeDetector: ChangeDetectorRef,
  ) {}

  submit(): void {
    this.submitted = true;
    this.form.markAllAsTouched();
    this.clearServerMessages();

    if (this.form.invalid) {
      this.focusFirstInvalidField();
      return;
    }

    this.loading = true;
    const credentials = this.form.getRawValue();

    this.loginService.login(credentials).subscribe({
      next: (response) => {
        this.loading = false;
        this.successMessage =
          `Inicio de sesión correcto para ${response.user.username}.`;

        // El token no se muestra ni se persiste.
        // Su integración con el estado de autenticación está pendiente de definición.
      },
      error: (error: unknown) => {
        this.loading = false;
        this.apiErrorMessage = this.messageForError(error);

        // Hace presente el resumen condicional antes de mover el foco.
        this.changeDetector.detectChanges();
        this.errorSummary?.nativeElement.focus();
      },
    });
  }

  clearServerMessages(): void {
    this.apiErrorMessage = null;
    this.successMessage = null;
  }

  showUsernameError(): boolean {
    const control = this.form.get('username');
    return Boolean(control?.invalid && (control.touched || this.submitted));
  }

  showPasswordError(): boolean {
    const control = this.form.get('password');
    return Boolean(control?.invalid && (control.touched || this.submitted));
  }

  private focusFirstInvalidField(): void {
    if (this.form.get('username')?.invalid) {
      this.usernameInput?.nativeElement.focus();
      return;
    }

    if (this.form.get('password')?.invalid) {
      this.passwordInput?.nativeElement.focus();
    }
  }

  private messageForError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Usuario o contraseña incorrectos.';
    }

    // Los significados/payloads de estos estados no están especificados
    // en el contrato disponible. No se expone el error técnico del servidor.
    if (
      error instanceof HttpErrorResponse &&
      error.status >= 500
    ) {
      return 'No se pudo completar el inicio de sesión. Inténtalo de nuevo más tarde.';
    }

    return 'No se pudo completar el inicio de sesión. Inténtalo de nuevo.';
  }
}