package com.ledgercore.ledger;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only ledger repository: exposes insert + read only, never update or delete
 * (Requirement 5.7). The application layer therefore cannot mutate posted entries.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Sum of credit amounts for an account (Requirements 4.4, 5.2).
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.direction = com.ledgercore.ledger.EntryDirection.CREDIT
            """)
    java.math.BigDecimal sumCredits(@Param("accountId") UUID accountId);

    /**
     * Sum of debit amounts for an account (Requirements 4.4, 5.2).
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.direction = com.ledgercore.ledger.EntryDirection.DEBIT
            """)
    java.math.BigDecimal sumDebits(@Param("accountId") UUID accountId);

    long countByAccountId(UUID accountId);

    /**
     * Sum of credit amounts up to and including a cursor position (for statement opening
     * balances). Requirement 10.3.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.direction = com.ledgercore.ledger.EntryDirection.CREDIT
              AND (e.postedAt < :ts OR (e.postedAt = :ts AND e.id <= :id))
            """)
    java.math.BigDecimal sumCreditsUpTo(@Param("accountId") UUID accountId,
                                        @Param("ts") Instant ts, @Param("id") long id);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.direction = com.ledgercore.ledger.EntryDirection.DEBIT
              AND (e.postedAt < :ts OR (e.postedAt = :ts AND e.id <= :id))
            """)
    java.math.BigDecimal sumDebitsUpTo(@Param("accountId") UUID accountId,
                                       @Param("ts") Instant ts, @Param("id") long id);

    /**
     * Keyset (cursor) pagination over an account's entries, ordered by (posted_at, id)
     * ascending, optionally constrained to an inclusive date range, returning entries
     * strictly after the supplied cursor position.
     *
     * <p>Requirements: 10.1 (ordering), 10.2 (date range), 10.4 (pagination).</p>
     */
    @Query("""
            SELECT e FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.postedAt >= :fromTs
              AND e.postedAt <= :toTs
              AND (e.postedAt > :afterTs OR (e.postedAt = :afterTs AND e.id > :afterId))
            ORDER BY e.postedAt ASC, e.id ASC
            """)
    List<LedgerEntry> findPage(@Param("accountId") UUID accountId,
                               @Param("fromTs") Instant fromTs,
                               @Param("toTs") Instant toTs,
                               @Param("afterTs") Instant afterTs,
                               @Param("afterId") long afterId,
                               Pageable pageable);
}
