package com.ledgercore.transfer;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the pure overdraft and lock-ordering policies.
 */
class TransferPolicyPropertiesTest {

    // Feature: ledger-core-banking, Property 22: Overdraft limit is never breached (single or concurrent)
    @Property(tries = 500)
    void property22_overdraftLimitNeverBreached(@ForAll @IntRange(min = -100000, max = 100000) int balMinor,
                                                @ForAll @IntRange(min = 1, max = 100000) int amtMinor,
                                                @ForAll @IntRange(min = 0, max = 50000) int limitMinor) {
        BigDecimal balance = BigDecimal.valueOf(balMinor, 2);
        BigDecimal amount = BigDecimal.valueOf(amtMinor, 2);
        BigDecimal limit = BigDecimal.valueOf(limitMinor, 2);

        boolean allowed = OverdraftPolicy.canWithdraw(balance, amount, limit);

        // Allowed exactly when the resulting balance stays at or above -limit.
        BigDecimal resulting = balance.subtract(amount);
        boolean expected = resulting.compareTo(limit.negate()) >= 0;
        assertThat(allowed).isEqualTo(expected);

        // When allowed, the post-withdrawal balance never breaches the floor.
        if (allowed) {
            assertThat(resulting).isGreaterThanOrEqualTo(limit.negate());
        }
    }

    // Feature: ledger-core-banking, Property 24: Lock acquisition order is deterministic and ascending
    @Property(tries = 500)
    void property24_lockOrderDeterministicAndAscending(@ForAll long a, @ForAll long b,
                                                       @ForAll long c) {
        UUID idA = new UUID(0, a);
        UUID idB = new UUID(0, b);
        UUID idC = new UUID(0, c);

        List<UUID> order = LockOrdering.order(idA, idB, idC, idA, idB);

        // Strictly ascending.
        for (int i = 1; i < order.size(); i++) {
            assertThat(order.get(i - 1).compareTo(order.get(i))).isLessThan(0);
        }
        // No duplicates; contains exactly the distinct inputs.
        assertThat(order).doesNotHaveDuplicates();
        assertThat(order).containsExactlyInAnyOrder(
                java.util.stream.Stream.of(idA, idB, idC).distinct().toArray(UUID[]::new));
        // Deterministic: same inputs in any argument order yield the same result.
        assertThat(LockOrdering.order(idC, idB, idA)).isEqualTo(order);
    }
}
