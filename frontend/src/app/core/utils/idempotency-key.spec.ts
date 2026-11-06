import { generateIdempotencyKey, IdempotencyKeyTracker, paymentFingerprint } from './idempotency-key';

describe('generateIdempotencyKey', () => {
  it('generates a non-empty string in UUID-like format', () => {
    const key = generateIdempotencyKey();
    expect(key).toBeTruthy();
    expect(key).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it('generates unique keys across calls', () => {
    const keys = new Set(Array.from({ length: 20 }, () => generateIdempotencyKey()));
    expect(keys.size).toBe(20);
  });
});

describe('paymentFingerprint', () => {
  it('combines recipient, amount and qrId into a stable string', () => {
    expect(paymentFingerprint('contact-demo', 1500, null)).toBe('contact-demo|1500|');
    expect(paymentFingerprint('merchant-demo', 2500, 'qr_1')).toBe('merchant-demo|2500|qr_1');
  });
});

describe('IdempotencyKeyTracker', () => {
  let tracker: IdempotencyKeyTracker;

  beforeEach(() => {
    tracker = new IdempotencyKeyTracker();
  });

  it('reuses the same key for the same attempt (same recipient/amount/qrId)', () => {
    const firstKey = tracker.keyFor('contact-demo', 1500, null);
    const secondKey = tracker.keyFor('contact-demo', 1500, null);
    expect(secondKey).toBe(firstKey);
  });

  it('generates a new key when the amount changes', () => {
    const firstKey = tracker.keyFor('contact-demo', 1500, null);
    const secondKey = tracker.keyFor('contact-demo', 3000, null);
    expect(secondKey).not.toBe(firstKey);
  });

  it('generates a new key when the recipient changes', () => {
    const firstKey = tracker.keyFor('contact-demo', 1500, null);
    const secondKey = tracker.keyFor('merchant-demo', 1500, null);
    expect(secondKey).not.toBe(firstKey);
  });

  it('generates a new key after reset even for the same attempt', () => {
    const firstKey = tracker.keyFor('contact-demo', 1500, null);
    tracker.reset();
    const secondKey = tracker.keyFor('contact-demo', 1500, null);
    expect(secondKey).not.toBe(firstKey);
  });
});
