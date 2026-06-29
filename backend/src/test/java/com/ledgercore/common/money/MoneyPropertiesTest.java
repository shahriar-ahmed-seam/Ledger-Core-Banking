package com.ledgercore.common.money;

import com.ledgercore.common.error.DomainException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for the Money primitive and amount validation.
 */
class MoneyPropertiesTest {

    @Provide
    Arbitrary<String> supportedCurrency() {
        return Arbitraries.of(Currencies.supported().toArray(new String[0]));
    }

    /**
     * Generates a Money value with a scale valid for the given currency.
     */
    private Arbitrary<Money> moneyIn(String currency) {
        int scale = Currencies.minorUnits(currency);
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-1_000_000), BigDecimal.valueOf(1_000_000))
                .ofScale(scale)
                .map(bd -> Money.of(bd, currency));
    }

    // Feature: ledger-core-banking, Property 19: Monetary arithmetic is decimal-exact
    @Property(tries = 200)
    void property19_monetaryArithmeticIsDecimalExact(@ForAll("supportedCurrency") String ccy,
                                                      @ForAll @IntRange(min = -100000, max = 100000) int aMinor,
                                                      @ForAll @IntRange(min = -100000, max = 100000) int bMinor) {
        int scale = Currencies.minorUnits(ccy);
        BigDecimal a = BigDecimal.valueOf(aMinor).movePointLeft(scale);
        BigDecimal b = BigDecimal.valueOf(bMinor).movePointLeft(scale);
        Money ma = Money.of(a, ccy);
        Money mb = Money.of(b, ccy);

        Money sum = ma.add(mb);
        Money diff = ma.subtract(mb);

        // Exact decimal expectations computed independently, at the currency scale.
        assertThat(sum.amount()).isEqualByComparingTo(a.add(b).setScale(scale, RoundingMode.UNNECESSARY));
        assertThat(diff.amount()).isEqualByComparingTo(a.subtract(b).setScale(scale, RoundingMode.UNNECESSARY));
        // Scale is preserved exactly; no floating-point drift.
        assertThat(sum.amount().scale()).isEqualTo(scale);
        // add then subtract is the identity.
        assertThat(ma.add(mb).subtract(mb)).isEqualTo(ma);
    }

    // Feature: ledger-core-banking, Property 36: Currency codes are validated
    @Property(tries = 200)
    void property36_currencyCodesAreValidated(@ForAll String code) {
        boolean supported = Currencies.isSupported(code);
        if (supported) {
            assertThat(Money.zero(code).currency()).isEqualTo(code);
        } else {
            assertThatThrownBy(() -> ApiAmount.parsePositiveOrZero("1", code))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).fields())
                            .anySatisfy(f -> assertThat(f.field()).isEqualTo("currency")));
        }
    }

    // Feature: ledger-core-banking, Property 37: Amount validation enforces non-negativity and currency precision
    @Property(tries = 300)
    void property37_amountValidationEnforcesNonNegativityAndPrecision(
            @ForAll("supportedCurrency") String ccy,
            @ForAll @IntRange(min = -5, max = 5) int requestedScale,
            @ForAll @IntRange(min = -1000, max = 1000) int unscaled) {
        int allowedScale = Currencies.minorUnits(ccy);
        BigDecimal value = BigDecimal.valueOf(unscaled, Math.max(requestedScale, 0));
        String asString = value.toPlainString();

        boolean shouldAccept = value.signum() >= 0 && value.scale() <= allowedScale;

        if (shouldAccept) {
            Money m = ApiAmount.parsePositiveOrZero(asString, ccy);
            assertThat(m.amount()).isEqualByComparingTo(value);
        } else {
            assertThatThrownBy(() -> ApiAmount.parsePositiveOrZero(asString, ccy))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).fields())
                            .anySatisfy(f -> assertThat(f.field()).isEqualTo("amount")));
        }
    }

    // Feature: ledger-core-banking, Property 38: Money serialization round-trip preserves value
    @Property(tries = 300)
    void property38_moneySerializationRoundTripPreservesValue(@ForAll("supportedCurrency") String ccy,
                                                              @ForAll @IntRange(min = -1000000, max = 1000000) int minor) {
        int scale = Currencies.minorUnits(ccy);
        Money original = Money.of(BigDecimal.valueOf(minor).movePointLeft(scale), ccy);

        String serialized = original.toDecimalString();
        Money parsed = Money.parse(serialized, ccy);

        assertTrue(original.equals(parsed),
                "round-trip must preserve value: " + original + " -> " + parsed);
    }
}
