/** Modelos compartidos del contrato BFF (ver CONTRACT.md). Todos los campos ya
 * vienen normalizados en camelCase por el backend. */

export interface ApiError {
  code: string;
  message: string;
  retryable: boolean;
  traceId: string;
}

// ---------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------

export type OnboardingStepId = 'IDENTITY' | 'DOCUMENT' | 'REVIEW' | 'ACTIVATION';
export type OnboardingStepStatus = 'PENDING' | 'CURRENT' | 'DONE';

export interface OnboardingStep {
  id: OnboardingStepId;
  status: OnboardingStepStatus;
}

export type WalletStatus = 'ACTIVE' | 'PENDING' | 'REJECTED' | 'IN_REVIEW' | null;

export interface OnboardingSession {
  onboardingId: string;
  currentStep: OnboardingStepId;
  steps: OnboardingStep[];
  walletId: string | null;
  walletStatus: WalletStatus;
}

export interface IdentityStepPayload {
  fullName: string;
  documentType: 'TEST';
  documentNumber: string;
}

export interface DocumentStepPayload {
  accept: boolean;
}

export type ReviewScenario = 'APPROVED' | 'REJECTED' | 'MANUAL_REVIEW' | 'UNAVAILABLE';

export interface ReviewStepPayload {
  scenario: ReviewScenario;
}

export interface ManualReviewDecisionPayload {
  decision: 'ACTIVE' | 'REJECTED';
}

export interface OnboardingAnalytics {
  medianOnboardingSeconds: number;
  monthly: {
    month: string;
    newActiveUsers: number;
    acquisitionSpendPen: number;
    cac: number;
  }[];
}

// ---------------------------------------------------------------------------
// Wallet
// ---------------------------------------------------------------------------

export interface Wallet {
  walletId: string;
  status: string;
  balanceCents: number;
  reservedCents: number;
  availableCents: number;
  currency: string;
}

// ---------------------------------------------------------------------------
// Pagos
// ---------------------------------------------------------------------------

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'IN_REVIEW' | 'REVERSED' | 'EXPIRED';
export type PaymentNextAction = 'RETRY' | 'BACK' | null;

export interface Payment {
  paymentId: string;
  walletId: string;
  recipient: string;
  amountCents: number;
  currency: string;
  status: PaymentStatus;
  reference: string;
  duplicate: boolean;
  nextAction: PaymentNextAction;
  createdAt?: string;
}

export interface CreatePaymentRequest {
  walletId: string;
  recipient: string;
  amountCents: number;
  scenario: ReviewScenario;
  qrId?: string | null;
}

export interface PaymentHistoryQuery {
  walletId?: string;
  recipient?: string;
  from?: string;
  to?: string;
  status?: PaymentStatus;
}

// ---------------------------------------------------------------------------
// QR / Comercio
// ---------------------------------------------------------------------------

export interface MerchantQr {
  qrId: string;
  payload: string;
  amountCents: number | null;
  currency: string;
  expiresAt: string;
}

export interface CreateQrRequest {
  amountCents?: number | null;
  expiresInSeconds: number;
}

export interface ResolvedQr {
  merchantId: string;
  merchantName: string;
  amountCents: number | null;
  currency: string;
}

export type MerchantPaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REVERSED';

export interface MerchantPayment {
  paymentId: string;
  qrId: string | null;
  amountCents: number;
  status: MerchantPaymentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface MerchantPaymentQuery {
  from?: string;
  to?: string;
  reference?: string;
}

// ---------------------------------------------------------------------------
// Soporte / Reversos
// ---------------------------------------------------------------------------

export type SupportReason = 'KYC_REVIEW' | 'UNKNOWN_PAYMENT_RESULT' | 'PROVIDER_PENDING';
export type SupportCaseStatus = 'OPEN' | 'RESOLVED';
export type EligibleAction = 'INVESTIGATE' | 'RETRY_ELIGIBLE' | 'DISPUTE_NOT_ELIGIBLE';

export interface SupportCase {
  caseId: string;
  resource: string;
  reason: SupportReason;
  status: SupportCaseStatus;
  resourceType: 'PAYMENT' | 'KYC';
  eligibleAction: EligibleAction;
}

export type SupportResolution = 'RETRY' | 'ESCALATE' | 'REJECT' | 'ACCEPT_DISPUTE';

export interface ResolveSupportCaseRequest {
  resolution: SupportResolution;
  note: string;
}

export type ReversalStatus = 'PENDING' | 'SUCCEEDED';

export interface Reversal {
  reversalId: string;
  paymentId: string;
  status: ReversalStatus;
}

// ---------------------------------------------------------------------------
// Operaciones / Conciliación
// ---------------------------------------------------------------------------

export interface LedgerEntry {
  id: number;
  operation: string;
  kind: 'FUNDING' | 'PAYMENT' | 'REVERSAL';
  account: string;
  amount: number;
  observedAt?: string;
}

export interface Settlement {
  id: string;
  amount: number;
  status: 'PENDING' | 'SUCCEEDED' | 'REJECTED' | 'REVERSED';
  observedAt?: string;
}

export type IncidentReason =
  | 'DUPLICATE_PROVIDER'
  | 'MISSING_WALLET'
  | 'MISSING_PROVIDER'
  | 'AMOUNT_MISMATCH'
  | 'STATUS_MISMATCH'
  | 'LEDGER_MISMATCH'
  | 'LEDGER_UNBALANCED'
  | 'BALANCE_MISMATCH';

export interface ReconciliationIncident {
  incidentId: string;
  paymentId: string;
  reason: IncidentReason;
  status: 'OPEN' | 'RESOLVED';
  channel?: string;
  observedAt?: string;
}

export interface ReconciliationSummary {
  date: string;
  status: 'MATCHED' | 'DIFFERENCES';
  totals: {
    reconciled: number;
    unreconciled: number;
    resolved: number;
  };
  incidents: ReconciliationIncident[];
}

export interface LedgerQuery {
  from?: string;
  to?: string;
  account?: string;
}

export interface IncidentsQuery {
  from?: string;
  to?: string;
  channel?: string;
}
