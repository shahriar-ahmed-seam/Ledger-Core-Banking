package com.ledgercore.it;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountService;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.error.ErrorCode;
import com.ledgercore.common.money.Money;
import com.ledgercore.transfer.TransferCommand;
import com.ledgercore.transfer.TransferService;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for concurrency-safe money movement against real PostgreSQL.
 *
 * <p>Covers Property 23 (serializable, value-conserving concurrent balance changes),
 * Property 22 (overdraft never breached under concurrency), and Task 8.4 (lock-wait timeout
 * yields a retryable error).</p>
 */
class TransferConcurrencyIT extends AbstractPostgresIT {

    @Autowired
    TransferService transferService;
    @Autowired
    AccountService accountService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DataSource dataSource;

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, id + "@example.com", "x", Role.CUSTOMER, Instant.now()));
        return id;
    }

    private void seed(UUID funding, UUID target, BigDecimal amount) {
        // Fund the target by transferring from a high-overdraft funding account.
        transferService.transfer(new TransferCommand(
                newUser(), "seed-" + UUID.randomUUID(), funding, target, amount.toPlainString(), "USD"));
    }

    // Feature: ledger-core-banking, Property 23: Concurrent balance changes are serializable and conserve value
    // Feature: ledger-core-banking, Property 22: Overdraft limit is never breached (single or concurrent)
    @Test
    void concurrentWithdrawalsAreSerializableAndConserveValue() throws Exception {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account source = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        Account sink = accountService.openAccount(owner, "USD", BigDecimal.ZERO);

        // Seed the source with exactly 100.00; each withdrawal moves 10.00. At most 10 succeed.
        seed(funding.getId(), source.getId(), new BigDecimal("100.00"));

        int attempts = 25;
        BigDecimal each = new BigDecimal("10.00");
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String key = "w-" + i;
            tasks.add(() -> {
                try {
                    transferService.transfer(new TransferCommand(
                            owner, key, source.getId(), sink.getId(), each.toPlainString(), "USD"));
                    return true;
                } catch (DomainException e) {
                    // Insufficient funds or retryable are acceptable rejections.
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();

        AtomicInteger succeeded = new AtomicInteger();
        for (Future<Boolean> f : results) {
            if (f.get()) {
                succeeded.incrementAndGet();
            }
        }

        Money sourceBalance = accountService.getAvailableBalance(source.getId());
        Money sinkBalance = accountService.getAvailableBalance(sink.getId());

        // Serializable outcome: final balance equals applying exactly the successful withdrawals.
        BigDecimal expectedSource = new BigDecimal("100.00")
                .subtract(each.multiply(BigDecimal.valueOf(succeeded.get())));
        assertThat(sourceBalance.amount()).isEqualByComparingTo(expectedSource);
        // Value conserved: everything withdrawn from source landed in sink.
        assertThat(sinkBalance.amount()).isEqualByComparingTo(each.multiply(BigDecimal.valueOf(succeeded.get())));
        // Overdraft floor (zero) never breached.
        assertThat(sourceBalance.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // No more than the affordable number of withdrawals committed.
        assertThat(succeeded.get()).isLessThanOrEqualTo(10);
    }

    // Task 8.4: a contended row lock that cannot be acquired within the timeout yields RETRYABLE_CONFLICT.
    @Test
    void lockWaitTimeoutYieldsRetryableError() throws Exception {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account source = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        Account sink = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        seed(funding.getId(), source.getId(), new BigDecimal("50.00"));

        // Hold a row lock on the source account in a separate connection.
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var st = holder.prepareStatement("SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
                st.setObject(1, source.getId());
                st.executeQuery();

                // While the lock is held, a transfer from the same source must time out and
                // return a retryable error (lock_timeout default 5s).
                DomainException ex = org.junit.jupiter.api.Assertions.assertThrows(DomainException.class,
                        () -> transferService.transfer(new TransferCommand(
                                owner, "blocked-1", source.getId(), sink.getId(), "10.00", "USD")));
                assertThat(ex.code()).isEqualTo(ErrorCode.RETRYABLE_CONFLICT);
            } finally {
                holder.rollback();
            }
        }

        // After the lock is released, the same logical transfer succeeds on retry.
        transferService.transfer(new TransferCommand(
                owner, "blocked-1", source.getId(), sink.getId(), "10.00", "USD"));
        assertThat(accountService.getAvailableBalance(source.getId()).amount())
                .isEqualByComparingTo(new BigDecimal("40.00"));
    }
}
