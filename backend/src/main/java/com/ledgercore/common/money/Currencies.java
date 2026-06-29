package com.ledgercore.common.money;

import java.util.Currency;
import java.util.Set;

/**
 * Supported ISO 4217 currencies and minor-unit precision lookup.
 *
 * <p>Requirements: 4.8, 12.6 (currency validation), 12.3 (minor-unit precision).</p>
 */
public final class Currencies {

    /** The set of currencies this banking engine supports. */
    private static final Set<String> SUPPORTED = Set.of(
            "BDT", "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "INR", "SGD", "ZAR"
    );

    private Currencies() {
    }

    /**
     * @return {@code true} if {@code code} is a supported ISO 4217 three-letter code.
     */
    public static boolean isSupported(String code) {
        return code != null && SUPPORTED.contains(code);
    }

    /**
     * Returns the number of minor-unit decimal places for the given currency
     * (for example 2 for USD, 0 for JPY).
     *
     * @throws IllegalArgumentException if the currency is not supported.
     */
    public static int minorUnits(String code) {
        if (!isSupported(code)) {
            throw new IllegalArgumentException("Unsupported currency: " + code);
        }
        int fractionDigits = Currency.getInstance(code).getDefaultFractionDigits();
        return Math.max(fractionDigits, 0);
    }

    public static Set<String> supported() {
        return SUPPORTED;
    }
}
