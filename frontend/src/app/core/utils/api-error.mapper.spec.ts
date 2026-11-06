import { HttpErrorResponse } from '@angular/common/http';
import { friendlyErrorMessage, mapHttpErrorToApiError } from './api-error.mapper';

describe('mapHttpErrorToApiError', () => {
  it('maps a backend error body with the unified BFF format', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        code: 'VALIDATION_ERROR',
        message: 'El documento debe iniciar con TEST-',
        retryable: false,
        traceId: 'trace-1'
      }
    });

    const apiError = mapHttpErrorToApiError(error);

    expect(apiError).toEqual({
      code: 'VALIDATION_ERROR',
      message: 'El documento debe iniciar con TEST-',
      retryable: false,
      traceId: 'trace-1'
    });
  });

  it('produces a generic UPSTREAM_UNAVAILABLE error for network failures (status 0)', () => {
    const error = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    const apiError = mapHttpErrorToApiError(error);

    expect(apiError.code).toBe('UPSTREAM_UNAVAILABLE');
    expect(apiError.retryable).toBeTrue();
    expect(apiError.message).not.toContain('ProgressEvent');
  });

  it('produces a generic error for unexpected 5xx responses without leaking internals', () => {
    const error = new HttpErrorResponse({
      status: 500,
      error: '<html>Internal Server Error - stacktrace...</html>'
    });

    const apiError = mapHttpErrorToApiError(error);

    expect(apiError.code).toBe('HTTP_500');
    expect(apiError.retryable).toBeTrue();
    expect(apiError.message).not.toContain('stacktrace');
  });

  it('marks 4xx generic errors as non-retryable', () => {
    const error = new HttpErrorResponse({ status: 404, error: 'not json' });

    const apiError = mapHttpErrorToApiError(error);

    expect(apiError.code).toBe('HTTP_404');
    expect(apiError.retryable).toBeFalse();
  });
});

describe('friendlyErrorMessage', () => {
  it('returns the known friendly message for a mapped business code', () => {
    const message = friendlyErrorMessage({
      code: 'LOW_VALUE_LIMIT_EXCEEDED',
      message: 'raw message',
      retryable: false,
      traceId: 't1'
    });

    expect(message).toContain('S/ 500.00');
  });

  it('falls back to the raw message for an unmapped code', () => {
    const message = friendlyErrorMessage({
      code: 'SOME_UNKNOWN_CODE',
      message: 'texto original del backend',
      retryable: false,
      traceId: 't2'
    });

    expect(message).toBe('texto original del backend');
  });
});
