package com.ledgercore.ledger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the pure double-entry posting logic.
 */
class LedgerPostingPropertiesTest {

    private static final String CCY = "USD";

    private Arbitrary<PostingLine> anyLine() {
        Arbitrary<UUID> accounts = Arbitraries.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));
        Arbitrary<EntryDirection> dirs = Arbitraries.of(EntryDirection.class);
        Arbitrary<BigDecimal> amounts = Arbitraries.integers().between(-500, 500)
                .map(i -> BigDecimal.valueOf(i, 2));
        return Combinators.combine(accounts, dirs, amounts).as(PostingLine::new);
    }

    @Provide
    Arbitrary<List<PostingLine>> arbitraryLineSets() {
        return anyLine().list().ofMinSize(0).ofMaxSize(6);
    }

    // Feature: ledger-core-banking, Property 16: Posting is accepted iff well-formed, else nothing is persisted
    @Property(tries = 500)
    void property16_postingAcceptedIffWellFormed(@ForAll("arbitraryLineSets") List<PostingLine> lines) {
        // Independent oracle of "well-formed": >=2 lines, all amounts > 0, >=1 debit and >=1 credit,
        // and sum(debits) == sum(credits).
        boolean atLeastTwo = lines.size() >= 2;
        boolean allPositive = lines.stream().allMatch(l -> l.amount().signum() > 0);
        boolean hasDebit = lines.stream().anyMatch(l -> l.direction() == EntryDirection.DEBIT);
        boolean hasCredit = lines.stream().anyMatch(l -> l.direction() == EntryDirection.CREDIT);
        BigDecimal debits = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT)
                .map(PostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT)
                .map(PostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanced = debits.compareTo(credits) == 0;

        boolean expectedValid = atLeastTwo && allPositive && hasDebit && hasCredit && balanced;

        assertThat(PostingValidator.isValid(lines, CCY)).isEqualTo(expectedValid);
    }

    // Feature: ledger-core-banking, Property 18: Reversal nets to zero and preserves the original
    @Property(tries = 300)
    void property18_reversalNetsToZero(@ForAll @IntRange(min = 1, max = 20) int legs,
                                       @ForAll @IntRange(min = 1, max = 1000) int unit) {
        // Build a balanced original: a credit to acct A and a matching debit from acct B, scaled.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        BigDecimal total = BigDecimal.valueOf((long) legs * unit, 2);
        List<PostingLine> original = new ArrayList<>();
        original.add(PostingLine.debit(b, total));
        original.add(PostingLine.credit(a, total));

        assertThat(PostingValidator.isValid(original, CCY)).isTrue();

        List<PostingLine> reversal = PostingValidator.reverse(original);
        assertThat(PostingValidator.isValid(reversal, CCY)).isTrue();

        // The combined net effect on every account is exactly zero.
        for (UUID acct : List.of(a, b)) {
            BigDecimal combined = PostingValidator.netEffect(original, acct)
                    .add(PostingValidator.netEffect(reversal, acct));
            assertThat(combined.signum()).isZero();
        }
    }
}
