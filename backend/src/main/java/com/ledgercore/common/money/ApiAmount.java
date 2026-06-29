package com.ledgercore.common.money;

import com.ledgercore.common.error.DomainException;

import java.math.BigDecimal;

/**
 * Validates and parses monetary amounts arriving over the API.
 *
 * <p>Requirements: 12.3 (precision), 12.6 (currency), 12.7 (valid non-negative decimal).</p>
 */
public final class ApiAmount {

    private ApiAmount() {
    }

    /**
     * Parses an API amount, enforcing: supported currency, valid non-negative decimal string,
     * and scale within the currency's minor-unit precision.
     *
     * @throws DomainException (VALIDATION_ERROR) identifying the offending field.
     */
    public static Money parsePositiveOrZero(String decimalString, String currency) {
        if (!Currencies.isSupported(currency)) {
            throw DomainException.validation(
                    "Unsupported currency.", "currency", "not a supported ISO 4217 code");
        }
        if (decimalString == null || decimalString.isBlank()) {
            throw DomainException.validation(
                    "Amount is required.", "amount", "must be a non-negative decimal string");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(decimalString.trim());
        } catch (NumberFormatException e) {
            throw DomainException.validation(
                    "Amount is not a valid decimal.", "amount", "must be a valid decimal string");
        }
        if (value.signum() < 0) {
            throw DomainException.validation(
                    "Amount must be non-negative.", "amount", "must be non-negative");
        }
        int scale = Currencies.minorUnits(currency);
        if (value.scale() > scale) {
            throw DomainException.validation(
                    "Amount exceeds currency precision.", "amount",
                    "scale exceeds " + scale + " decimal places for " + currency);
        }
        return Money.of(value, currency);
    }
}
