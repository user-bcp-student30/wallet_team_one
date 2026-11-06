import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent)
  },
  {
    path: 'onboarding',
    loadComponent: () => import('./features/onboarding/onboarding.component').then((m) => m.OnboardingComponent)
  },
  {
    path: 'pagos/nuevo',
    loadComponent: () =>
      import('./features/payments/payment-create.component').then((m) => m.PaymentCreateComponent)
  },
  {
    path: 'pagos/historial',
    loadComponent: () =>
      import('./features/payments/payment-history.component').then((m) => m.PaymentHistoryComponent)
  },
  {
    path: 'comercio',
    loadComponent: () =>
      import('./features/merchant/merchant-dashboard.component').then((m) => m.MerchantDashboardComponent)
  },
  {
    path: 'soporte',
    loadComponent: () => import('./features/support/support.component').then((m) => m.SupportComponent)
  },
  {
    path: 'operaciones',
    loadComponent: () => import('./features/operations/operations.component').then((m) => m.OperationsComponent)
  },
  { path: '**', redirectTo: '' }
];
