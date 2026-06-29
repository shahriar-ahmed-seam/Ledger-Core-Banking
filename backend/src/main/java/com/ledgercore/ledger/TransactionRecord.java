package com.ledgercore.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A balanced group of two or more {@link LedgerEntry} rows representing one financial
 * event (Requirement 5.1). A correction is recorded as a new transaction whose
 * {@code reversesTxnId} references the original (Requirement 5.8).
 *
 * <p>Named {@code TransactionRecord} to avoid clashing with database/transaction concepts.</p>
 */
@Entity
@Table(name = "transactions")
public class TransactionRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reverses_txn_id")
    private UUID reversesTxnId;

    @Column(name = "reference")
    private String reference;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected TransactionRecord() {
    }

    public TransactionRecord(UUID id, String currency, UUID reversesTxnId, String reference,
                             Instant postedAt) {
        this.id = id;
        this.currency = currency;
        this.reversesTxnId = reversesTxnId;
        this.reference = reference;
        this.postedAt = postedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getReversesTxnId() {
        return reversesTxnId;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
