package com.ledgercore.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable monetary value: a fixed-precision {@link BigDecimal} amount plus an ISO 4217
 * currency. No binary floating-point representation is ever used for money.
 *
 * <p>Requirements: 5.9 (fixed-precision decimal, no floating point), 12.2 (decimal-string
 * representation with explicit currency).</p>
 */
public final class Money implements Comparable<Money> {

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * Creates a Money value, normalizing the scale to the currency's minor units.
     *
     * @throws IllegalArgumentException if the currency is unsupported or the amount's scale
     *                                  exceeds the currency's minor-unit precision.
     */
    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (!Currencies.isSupported(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        int scale = Currencies.minorUnits(currency);
        try {
            // Normalize to the currency scale. This succeeds when any digits beyond the
            // currency precision are zero (e.g. a NUMERIC(38,4) sum of 100.0000 -> 100.00)
            // and throws only when real rounding would be required. API-level input precision
            // is validated separately by ApiAmount.
            return new Money(amount.setScale(scale, RoundingMode.UNNECESSARY), currency);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount " + amount.toPlainString() + " exceeds minor-unit precision "
                            + scale + " for currency " + currency);
        }
    }

    /**
     * Parses a decimal-string amount (as carried over the API) for the given currency.
     *
     * @throws IllegalArgumentException if the string is not a valid decimal or violates
     *                                  currency precision.
     */
    public static Money parse(String decimalString, String currency) {
        Objects.requireNonNull(decimalString, "decimalString");
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(decimalString.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal amount: " + decimalString, e);
        }
        return of(parsed, currency);
    }

    public static Money zero(String currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    /**
     * @return the API representation of the amount as a fixed-scale decimal string
     *         (Requirement 12.2).
     */
    public String toDecimalString() {
        return amount.toPlainString();
    }

    @Override
    public int compareTo(Money o) {
        requireSameCurrency(o);
        return this.amount.compareTo(o.amount);
    }

    /** Value equality at the currency scale (never floating-point comparison). */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return toDecimalString() + " " + currency;
    }
}
