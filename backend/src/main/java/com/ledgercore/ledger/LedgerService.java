package com.ledgercore.ledger;

import com.ledgercore.common.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The single chokepoint for all ledger mutations. Enforces double-entry bookkeeping
 * invariants and persists entries append-only within one database transaction.
 *
 * <p>Requirements: 5 (double-entry integrity), 9.1 (atomic persistence).</p>
 */
@Service
public class LedgerService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    public LedgerService(TransactionRepository transactionRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         Clock clock) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.clock = clock;
    }

    /**
     * Validates and posts a balanced transaction. Either all entries persist or none do.
     *
     * @return the new transaction identifier.
     * @throws DomainException if the posting is not well-formed (nothing is persisted).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public UUID postTransaction(List<PostingLine> lines, String currency, UUID reversesTxnId,
                                String reference) {
        // Pure validation first: on failure nothing is written (Requirements 5.3, 5.5, 5.6).
        PostingValidator.validate(lines, currency);

        Instant postedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        UUID txnId = UUID.randomUUID();

        transactionRepository.save(
                new TransactionRecord(txnId, currency, reversesTxnId, reference, postedAt));

        for (PostingLine line : lines) {
            ledgerEntryRepository.save(new LedgerEntry(
                    txnId, line.accountId(), line.direction(), line.amount(), currency, postedAt));
        }
        return txnId;
    }

    /**
     * Records a correction as a new reversing transaction whose entries are equal in amount
     * and opposite in direction to the original, referencing the original's identifier.
     * The original entries are never modified or deleted (Requirement 5.8).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public UUID reverseTransaction(UUID originalTxnId) {
        TransactionRecord original = transactionRepository.findById(originalTxnId)
                .orElseThrow(() -> DomainException.notFound("Transaction not found: " + originalTxnId));
        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(originalTxnId);
        if (originalEntries.isEmpty()) {
            throw DomainException.notFound("No entries for transaction: " + originalTxnId);
        }
        List<PostingLine> reversing = originalEntries.stream()
                .map(e -> new PostingLine(e.getAccountId(), e.getDirection().flip(), e.getAmount()))
                .toList();
        return postTransaction(reversing, original.getCurrency(), originalTxnId,
                "reversal-of:" + originalTxnId);
    }

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 500;

    /**
     * Returns a page of an account's ledger entries ordered by (posted_at, id) ascending,
     * optionally constrained to an inclusive date range (Requirements 10.1, 10.2, 10.4,
     * 10.6, 10.7).
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntry> listEntries(UUID accountId, Instant from, Instant to,
                                         String cursor, Integer pageSize) {
        int size = clampPageSize(pageSize);
        Instant fromTs = from == null ? Instant.EPOCH : from;
        Instant toTs = to == null ? farFuture() : to;
        if (fromTs.isAfter(toTs)) {
            throw new com.ledgercore.common.error.DomainException(
                    com.ledgercore.common.error.ErrorCode.INVALID_DATE_RANGE,
                    "Date range start must not be after end.");          // R10.6
        }
        Cursor c = Cursor.decodeOrStart(cursor);                          // R10.7
        List<LedgerEntry> entries = ledgerEntryRepository.findPage(
                accountId, fromTs, toTs, c.postedAt(), c.id(),
                org.springframework.data.domain.PageRequest.of(0, size));
        return new Page<>(entries, nextCursor(entries, size));
    }

    /**
     * Returns a page of statement lines with a running balance (Requirement 10.3).
     */
    @Transactional(readOnly = true)
    public Page<StatementLine> statement(UUID accountId, Instant from, Instant to,
                                         String cursor, Integer pageSize) {
        int size = clampPageSize(pageSize);
        Instant fromTs = from == null ? Instant.EPOCH : from;
        Instant toTs = to == null ? farFuture() : to;
        if (fromTs.isAfter(toTs)) {
            throw new com.ledgercore.common.error.DomainException(
                    com.ledgercore.common.error.ErrorCode.INVALID_DATE_RANGE,
                    "Date range start must not be after end.");
        }
        Cursor c = Cursor.decodeOrStart(cursor);
        // Opening balance: signed sum of all entries up to and including the cursor position.
        java.math.BigDecimal opening = ledgerEntryRepository.sumCreditsUpTo(accountId, c.postedAt(), c.id())
                .subtract(ledgerEntryRepository.sumDebitsUpTo(accountId, c.postedAt(), c.id()));
        List<LedgerEntry> entries = ledgerEntryRepository.findPage(
                accountId, fromTs, toTs, c.postedAt(), c.id(),
                org.springframework.data.domain.PageRequest.of(0, size));
        List<StatementLine> lines = Statements.withRunningBalance(entries, opening);
        return new Page<>(lines, nextCursor(entries, size));
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(MIN_PAGE_SIZE, Math.min(requested, MAX_PAGE_SIZE));
    }

    private String nextCursor(List<LedgerEntry> entries, int size) {
        if (entries.size() < size || entries.isEmpty()) {
            return null;
        }
        LedgerEntry last = entries.get(entries.size() - 1);
        return new Cursor(last.getPostedAt(), last.getId()).encode();
    }

    private Instant farFuture() {
        return Instant.now(clock).plus(3650, ChronoUnit.DAYS);
    }
}
