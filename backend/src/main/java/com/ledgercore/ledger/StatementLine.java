package com.ledgercore.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A statement line: a ledger entry plus the running balance after it (Requirement 10.3).
 */
public record StatementLine(Long entryId, UUID transactionId, EntryDirection direction,
                            BigDecimal amount, Instant postedAt, BigDecimal runningBalance) {
}
