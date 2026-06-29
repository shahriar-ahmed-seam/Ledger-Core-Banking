import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { newIdempotencyKey } from '../lib/idempotency';

describe('idempotency key generation', () => {
  // Feature: ledger-core-banking, Property 40: Dashboard generates a well-formed unique idempotency key per submission
  it('property40_generatesWellFormedUniqueKeyPerSubmission', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 64 }), (count) => {
        const keys = Array.from({ length: count }, () => newIdempotencyKey());
        // Each key is a well-formed string of 1..128 characters.
        for (const key of keys) {
          expect(typeof key).toBe('string');
          expect(key.length).toBeGreaterThanOrEqual(1);
          expect(key.length).toBeLessThanOrEqual(128);
        }
        // Keys are distinct across distinct submissions.
        expect(new Set(keys).size).toBe(count);
      }),
      { numRuns: 100 },
    );
  });
});
