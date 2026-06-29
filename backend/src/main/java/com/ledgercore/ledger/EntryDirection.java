package com.ledgercore.ledger;

/**
 * The side of a ledger entry. Direction carries the sign; amounts are always positive.
 * (Requirement 5.9, design balance convention.)
 */
public enum EntryDirection {
    DEBIT,
    CREDIT;

    public EntryDirection flip() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
