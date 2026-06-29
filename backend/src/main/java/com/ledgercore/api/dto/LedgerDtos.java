package com.ledgercore.api.dto;

import com.ledgercore.ledger.LedgerEntry;
import com.ledgercore.ledger.Page;
import com.ledgercore.ledger.StatementLine;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for ledger queries and statements (Requirement 10).
 */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record EntryResponse(long id, UUID transactionId, UUID accountId, String direction,
                                String amount, String currency, Instant postedAt) {
        public static EntryResponse from(LedgerEntry e) {
            return new EntryResponse(e.getId(), e.getTransactionId(), e.getAccountId(),
                    e.getDirection().name(), e.getAmount().toPlainString(), e.getCurrency(),
                    e.getPostedAt());
        }
    }

    public record StatementLineResponse(Long entryId, UUID transactionId, String direction,
                                        String amount, Instant postedAt, String runningBalance) {
        public static StatementLineResponse from(StatementLine l) {
            return new StatementLineResponse(l.entryId(), l.transactionId(), l.direction().name(),
                    l.amount().toPlainString(), l.postedAt(), l.runningBalance().toPlainString());
        }
    }

    public record PageResponse<T>(List<T> items, String nextCursor) {
        public static PageResponse<EntryResponse> ofEntries(Page<LedgerEntry> page) {
            return new PageResponse<>(page.items().stream().map(EntryResponse::from).toList(),
                    page.nextCursor());
        }

        public static PageResponse<StatementLineResponse> ofStatement(Page<StatementLine> page) {
            return new PageResponse<>(page.items().stream().map(StatementLineResponse::from).toList(),
                    page.nextCursor());
        }
    }
}
