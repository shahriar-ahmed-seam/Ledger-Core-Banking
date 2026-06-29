/**
 * Currency-aware money formatting (Requirement 14.9): renders using the symbol and the
 * exact minor-unit decimal precision defined by the currency.
 */

/** Minor-unit decimal places per supported ISO 4217 currency. */
const MINOR_UNITS: Record<string, number> = {
  USD: 2, EUR: 2, GBP: 2, CHF: 2, CAD: 2, AUD: 2, INR: 2, SGD: 2, ZAR: 2,
  JPY: 0,
};

export function minorUnits(currency: string): number {
  return MINOR_UNITS[currency] ?? 2;
}

/**
 * Formats a decimal-string amount for a currency using the currency's symbol and exact
 * minor-unit precision.
 */
export function formatMoney(amount: string, currency: string): string {
  const digits = minorUnits(currency);
  const value = Number(amount);
  const safe = Number.isFinite(value) ? value : 0;
  try {
    return new Intl.NumberFormat('en', {
      style: 'currency',
      currency,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(safe);
  } catch {
    // Fallback if the runtime lacks the currency: fixed precision + code.
    return `${safe.toFixed(digits)} ${currency}`;
  }
}

/** Sign-aware class for positive/negative balance movements. */
export function balanceTone(amount: string): 'positive' | 'negative' | 'neutral' {
  const v = Number(amount);
  if (v > 0) return 'positive';
  if (v < 0) return 'negative';
  return 'neutral';
}
