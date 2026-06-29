import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { formatMoney, minorUnits } from '../lib/money';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'JPY', 'SGD', 'INR'];

/** Counts the fraction digits in a formatted currency string (locale 'en'). */
function fractionDigits(formatted: string): number {
  // Strip grouping commas, then look at digits after the last '.'.
  const cleaned = formatted.replace(/,/g, '');
  const dot = cleaned.lastIndexOf('.');
  if (dot === -1) return 0;
  const after = cleaned.slice(dot + 1).replace(/[^0-9]/g, '');
  return after.length;
}

describe('money formatting', () => {
  // Feature: ledger-core-banking, Property 41: Money rendering uses the currency's symbol and precision
  it('property41_rendersUsingCurrencySymbolAndPrecision', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...CURRENCIES),
        fc.integer({ min: 0, max: 1_000_000_000 }),
        (currency, minor) => {
          const digits = minorUnits(currency);
          const amount = (minor / Math.pow(10, digits)).toFixed(digits);

          const formatted = formatMoney(amount, currency);

          // Exact minor-unit precision for the currency.
          expect(fractionDigits(formatted)).toBe(digits);
          // Contains a currency indicator (symbol or code), i.e. more than just the number.
          const numericOnly = new Intl.NumberFormat('en', {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits,
          }).format(Number(amount));
          expect(formatted).not.toBe(numericOnly);
          // The numeric value is preserved (ignoring symbol/grouping).
          const parsed = Number(formatted.replace(/[^0-9.]/g, ''));
          expect(parsed).toBeCloseTo(Number(amount), digits);
        },
      ),
      { numRuns: 200 },
    );
  });
});
