/**
 * Generates a well-formed, unique idempotency key per money-movement submission
 * (Requirement 14.2, design Property 40).
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // Fallback: RFC4122-ish from random bytes.
  const bytes = new Uint8Array(16);
  (crypto as Crypto).getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
