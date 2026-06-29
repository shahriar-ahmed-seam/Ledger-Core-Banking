package com.ledgercore.transfer;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountRepository;
import com.ledgercore.account.AccountService;
import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.money.ApiAmount;
import com.ledgercore.common.money.Money;
import com.ledgercore.idempotency.IdempotencyResult;
import com.ledgercore.idempotency.IdempotencyService;
import com.ledgercore.ledger.LedgerService;
import com.ledgercore.ledger.PostingLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates concurrency-safe, idempotent, double-entry money movement.
 *
 * <p>This is the intersection of Requirements 6 (transfer), 7 (row-level locking and
 * serializable outcomes), 8 (idempotency), and 9 (ACID). See the design "Funds-Transfer &
 * Locking Algorithm".</p>
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final long lockTimeoutMillis;

    @PersistenceContext
    private EntityManager entityManager;

    public TransferService(AccountRepository accountRepository,
                           AccountService accountService,
                           LedgerService ledgerService,
                           IdempotencyService idempotencyService,
                           AuditService auditService,
                           @Value("${ledger.transfer.lock-timeout:PT5S}") Duration lockTimeout) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        // Requirement 7.7: default 5s, clamped to the configurable 1-30s range.
        long ms = lockTimeout.toMillis();
        this.lockTimeoutMillis = Math.max(1000L, Math.min(ms, 30_000L));
    }

    /**
     * Executes a transfer. The entire operation runs in one database transaction at READ
     * COMMITTED with explicit row locks held until commit (design isolation strategy).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResult transfer(TransferCommand command) {
        // --- Pre-lock validation (pure; no state change on failure). Requirements 6.2/6.3/6.5/8.5 ---
        IdempotencyService.validateKeyFormat(command.idempotencyKey());
        if (command.sourceId() == null || command.destinationId() == null) {
            throw DomainException.validation("Source and destination are required.",
                    "account", "must be provided");
        }
        if (command.sourceId().equals(command.destinationId())) {
            throw DomainException.validation("Source and destination must differ.",
                    "destinationId", "must not equal sourceId");          // R6.5
        }
        Money amount = ApiAmount.parsePositiveOrZero(command.amount(), command.currency());
        if (!amount.isPositive()) {
            throw DomainException.validation("Transfer amount must be positive.",
                    "amount", "must be greater than zero");                // R6.2
        }

        // --- Bound lock waiting for this transaction (Requirement 7.7) ---
        setLockTimeout();

        // --- Idempotency gate (Requirement 8). Runs in this transaction. ---
        String fingerprint = IdempotencyService.fingerprint(
                command.sourceId(), command.destinationId(), amount.toDecimalString(), command.currency());
        IdempotencyResult idem = idempotencyService.begin(command.idempotencyKey(), fingerprint);
        switch (idem.outcome()) {
            case REPLAY -> {
                return new TransferResult(idem.transactionId(), true);     // R8.2
            }
            case CONFLICT_IN_PROGRESS, CONFLICT_MISMATCH ->
                    throw DomainException.conflict(
                            "Idempotency key conflict for the supplied request.");  // R8.3/R8.4
            default -> { /* BEGUN: proceed */ }
        }

        // --- Deterministic, ordered row locking (Requirements 7.1, 7.2, 7.6) ---
        Map<UUID, Account> locked = lockInOrder(command.sourceId(), command.destinationId());
        Account source = locked.get(command.sourceId());
        Account destination = locked.get(command.destinationId());

        // --- Existence and status checks (Requirements 6.7, 4.7) ---
        if (source == null || destination == null) {
            throw DomainException.notFound("Source or destination account does not exist.");
        }
        accountService.ensureOpenForMovement(source);
        accountService.ensureOpenForMovement(destination);

        // --- Currency consistency (Requirement 6.3) ---
        if (!source.getCurrency().equals(destination.getCurrency())
                || !source.getCurrency().equals(command.currency())) {
            throw DomainException.validation("Account currencies must match the transfer.",
                    "currency", "source and destination currencies must match");
        }

        // --- Authoritative balance read under the lock (Requirement 9.3) ---
        Money sourceBalance = accountService.getAvailableBalance(source);

        // --- Overdraft check (Requirements 6.4, 7.4) ---
        if (!OverdraftPolicy.canWithdraw(sourceBalance.amount(), amount.amount(),
                source.getOverdraftLimit())) {
            throw DomainException.insufficientFunds(
                    "Available balance is insufficient for the requested amount.");
        }

        // --- Post one balanced transaction debiting source, crediting destination (R6.1, R5) ---
        UUID txnId = ledgerService.postTransaction(
                List.of(PostingLine.debit(source.getId(), amount.amount()),
                        PostingLine.credit(destination.getId(), amount.amount())),
                command.currency(), null,
                "transfer:" + command.sourceId() + "->" + command.destinationId());

        // --- Maintain the reconcilable balance cache under the lock ---
        source.setBalanceCache(sourceBalance.subtract(amount).amount());
        Money destBalance = accountService.getAvailableBalance(destination);
        destination.setBalanceCache(destBalance.amount());
        accountRepository.save(source);
        accountRepository.save(destination);

        // --- Complete idempotency record and audit, in the same transaction (R8.1, R11.1/11.6) ---
        idempotencyService.complete(command.idempotencyKey(), txnId);
        auditService.record(command.actorId(), AuditAction.TRANSFER, txnId.toString(), true,
                "transfer " + amount + " " + command.sourceId() + " -> " + command.destinationId());

        return new TransferResult(txnId, false);
    }

    /**
     * Acquires row locks on the given accounts in ascending-id order (Requirement 7.6),
     * translating lock-wait timeouts and deadlocks into a retryable error (Requirement 7.7).
     */
    private Map<UUID, Account> lockInOrder(UUID... accountIds) {
        Map<UUID, Account> result = new LinkedHashMap<>();
        try {
            for (UUID id : LockOrdering.order(accountIds)) {
                accountRepository.findByIdForUpdate(id).ifPresent(a -> result.put(id, a));
            }
        } catch (PessimisticLockingFailureException | QueryTimeoutException lockWait) {
            // 55P03 (lock_not_available) or 40P01 (deadlock) -> abort and signal retryable.
            throw DomainException.retryable(
                    "Could not acquire account locks in time; please retry.");
        }
        return result;
    }

    private void setLockTimeout() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = " + lockTimeoutMillis)
                .executeUpdate();
    }

    public long lockTimeoutMillis() {
        return lockTimeoutMillis;
    }
}
