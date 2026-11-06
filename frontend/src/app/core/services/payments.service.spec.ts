import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Payment } from '../models/wallet.models';
import { PaymentsService } from './payments.service';

describe('PaymentsService', () => {
  let service: PaymentsService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/payments`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PaymentsService]
    });
    service = TestBed.inject(PaymentsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends the Idempotency-Key header and the request body on creation', () => {
    const idempotencyKey = 'key-123';
    let result: Payment | undefined;

    service
      .createPayment(
        { walletId: 'wallet-demo', recipient: 'contact-demo', amountCents: 1500, scenario: 'APPROVED', qrId: null },
        idempotencyKey
      )
      .subscribe((payment) => (result = payment));

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);
    expect(req.request.body).toEqual({
      walletId: 'wallet-demo',
      recipient: 'contact-demo',
      amountCents: 1500,
      scenario: 'APPROVED',
      qrId: null
    });

    const response: Payment = {
      paymentId: 'pay_1',
      walletId: 'wallet-demo',
      recipient: 'contact-demo',
      amountCents: 1500,
      currency: 'PEN',
      status: 'SUCCEEDED',
      reference: 'pay_1',
      duplicate: false,
      nextAction: null
    };
    req.flush(response, { status: 201, statusText: 'Created' });

    expect(result).toEqual(response);
  });

  it('surfaces a duplicate=true response for a repeated Idempotency-Key without creating a new charge', () => {
    const idempotencyKey = 'key-456';
    let result: Payment | undefined;

    service
      .createPayment(
        { walletId: 'wallet-demo', recipient: 'contact-demo', amountCents: 2000, scenario: 'APPROVED', qrId: null },
        idempotencyKey
      )
      .subscribe((payment) => (result = payment));

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);

    const duplicateResponse: Payment = {
      paymentId: 'pay_2',
      walletId: 'wallet-demo',
      recipient: 'contact-demo',
      amountCents: 2000,
      currency: 'PEN',
      status: 'SUCCEEDED',
      reference: 'pay_2',
      duplicate: true,
      nextAction: null
    };
    // El backend responde 200 (no 201) cuando es una réplica de idempotencia.
    req.flush(duplicateResponse, { status: 200, statusText: 'OK' });

    expect(result?.duplicate).toBeTrue();
    expect(result?.paymentId).toBe('pay_2');
  });

  it('requests the payment history with the given filters as query params', () => {
    service.getHistory({ walletId: 'wallet-demo', recipient: 'contact-demo', status: 'SUCCEEDED' }).subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === baseUrl &&
        r.params.get('walletId') === 'wallet-demo' &&
        r.params.get('recipient') === 'contact-demo' &&
        r.params.get('status') === 'SUCCEEDED'
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
