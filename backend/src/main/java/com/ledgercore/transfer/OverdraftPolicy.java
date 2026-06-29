package com.ledgercore.transfer;

import java.math.BigDecimal;

/**
 * The overdraft rule: a withdrawal is permitted only if it keeps the resulting balance at
 * or above the negative of the overdraft limit (Requirements 6.4, 7.4).
 */
public final class OverdraftPolicy {

    private OverdraftPolicy() {
    }

    /**
     * @param currentBalance the source account's available balance before the withdrawal
     * @param amount         the withdrawal amount (positive)
     * @param overdraftLimit the non-negative overdraft limit
     * @return {@code true} if {@code currentBalance - amount >= -overdraftLimit}.
     */
    public static boolean canWithdraw(BigDecimal currentBalance, BigDecimal amount,
                                      BigDecimal overdraftLimit) {
        BigDecimal resulting = currentBalance.subtract(amount);
        BigDecimal floor = overdraftLimit.negate();
        return resulting.compareTo(floor) >= 0;
    }
}
