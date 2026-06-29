package com.ledgercore.ledger;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure property test for statement running-balance computation.
 */
class StatementPropertiesTest {

    // Feature: ledger-core-banking, Property 31: Statement running balance equals the prefix sum
    @Property(tries = 300)
    void property31_runningBalanceEqualsPrefixSum(
            @ForAll @Size(min = 0, max = 40) List<@net.jqwik.api.constraints.IntRange(min = -10000, max = 10000) Integer> minors) {
        UUID account = UUID.randomUUID();
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        List<LedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < minors.size(); i++) {
            int m = minors.get(i);
            EntryDirection dir = m >= 0 ? EntryDirection.CREDIT : EntryDirection.DEBIT;
            BigDecimal amount = BigDecimal.valueOf(Math.abs((long) m), 2).max(new BigDecimal("0.01"));
            entries.add(new LedgerEntry(UUID.randomUUID(), account, dir, amount, "USD", base.plusSeconds(i)));
        }

        BigDecimal opening = new BigDecimal("100.00");
        List<StatementLine> lines = Statements.withRunningBalance(entries, opening);

        // Each running balance equals opening + cumulative signed sum up to and including i.
        BigDecimal expected = opening;
        for (int i = 0; i < entries.size(); i++) {
            expected = expected.add(entries.get(i).signedAmount());
            assertThat(lines.get(i).runningBalance()).isEqualByComparingTo(expected);
        }
        assertThat(lines).hasSameSizeAs(entries);
    }
}
