package com.ledgercore.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a transaction to be posted: a debit or credit of a positive amount against
 * one account. All lines in a posting share a single currency.
 */
public record PostingLine(UUID accountId, EntryDirection direction, BigDecimal amount) {

    public PostingLine {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
    }

    public static PostingLine debit(UUID accountId, BigDecimal amount) {
        return new PostingLine(accountId, EntryDirection.DEBIT, amount);
    }

    public static PostingLine credit(UUID accountId, BigDecimal amount) {
        return new PostingLine(accountId, EntryDirection.CREDIT, amount);
    }
}
