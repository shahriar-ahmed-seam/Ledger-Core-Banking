package com.ledgercore.ledger;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Insert + read only. Transactions are never updated or deleted (Requirement 5.7).
 */
public interface TransactionRepository extends Repository<TransactionRecord, UUID> {

    TransactionRecord save(TransactionRecord txn);

    Optional<TransactionRecord> findById(UUID id);
}
