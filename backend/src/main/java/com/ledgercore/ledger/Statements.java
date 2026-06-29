package com.ledgercore.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure running-balance computation for statements (Requirement 10.3).
 */
public final class Statements {

    private Statements() {
    }

    /**
     * Produces statement lines whose running balance after each entry equals the opening
     * balance plus the cumulative signed sum of entries up to and including that entry.
     *
     * @param orderedEntries entries in ascending (postedAt, id) order
     * @param openingBalance the balance of all entries strictly before the first entry here
     */
    public static List<StatementLine> withRunningBalance(List<LedgerEntry> orderedEntries,
                                                         BigDecimal openingBalance) {
        List<StatementLine> lines = new ArrayList<>(orderedEntries.size());
        BigDecimal running = openingBalance;
        for (LedgerEntry e : orderedEntries) {
            running = running.add(e.signedAmount());
            lines.add(new StatementLine(e.getId(), e.getTransactionId(), e.getDirection(),
                    e.getAmount(), e.getPostedAt(), running));
        }
        return lines;
    }
}
