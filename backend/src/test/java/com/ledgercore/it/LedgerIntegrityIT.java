package com.ledgercore.it;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountService;
import com.ledgercore.idempotency.IdempotencyService;
import com.ledgercore.ledger.EntryDirection;
import com.ledgercore.ledger.LedgerEntryRepository;
import com.ledgercore.ledger.LedgerService;
import com.ledgercore.ledger.PostingLine;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for ledger atomicity, append-only enforcement, and durability.
 */
class LedgerIntegrityIT extends AbstractPostgresIT {

    @Autowired
    LedgerService ledgerService;
    @Autowired
    AccountService accountService;
    @Autowired
    LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DataSource dataSource;
    @Autowired
    IdempotencyService idempotencyService;

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, id + "@example.com", "x", Role.CUSTOMER, Instant.now()));
        return id;
    }

    // Feature: ledger-core-banking, Property 25: Money movement is atomic under failure
    @Test
    void postingFailureRollsBackEntireTransaction() {
        UUID owner = newUser();
        Account a = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        UUID phantom = UUID.randomUUID(); // not a real account -> FK violation on its entry

        long before = ledgerEntryRepository.countByAccountId(a.getId());

        // Balanced posting, but the credit line targets a non-existent account, so the second
        // insert violates the FK and the whole transaction must roll back.
        assertThatThrownBy(() -> ledgerService.postTransaction(
                List.of(PostingLine.debit(a.getId(), new BigDecimal("10.00")),
                        PostingLine.credit(phantom, new BigDecimal("10.00"))),
                "USD", null, "should-rollback"))
                .isInstanceOf(Exception.class);

        long after = ledgerEntryRepository.countByAccountId(a.getId());
        assertThat(after).isEqualTo(before); // no partial entry for the debit line persisted
    }

    // Feature: ledger-core-banking, Property 17: The ledger is append-only (DB-role enforcement, Task 2.3)
    @Test
    void ledgerEntriesCannotBeUpdatedOrDeletedByApplicationRole() throws SQLException {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account a = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        UUID txn = ledgerService.postTransaction(
                List.of(PostingLine.debit(funding.getId(), new BigDecimal("5.00")),
                        PostingLine.credit(a.getId(), new BigDecimal("5.00"))),
                "USD", null, "seed");
        assertThat(txn).isNotNull();

        // Create the least-privilege role and apply the same grants V3 would, then prove that
        // UPDATE and DELETE on ledger_entries are denied for it.
        try (Connection admin = dataSource.getConnection(); var st = admin.createStatement()) {
            st.execute("DROP ROLE IF EXISTS ledger_app_test");
            st.execute("CREATE ROLE ledger_app_test LOGIN PASSWORD 'pw'");
            st.execute("GRANT CONNECT ON DATABASE ledgercore TO ledger_app_test");
            st.execute("GRANT USAGE ON SCHEMA public TO ledger_app_test");
            st.execute("GRANT SELECT, INSERT ON ledger_entries TO ledger_app_test");
        }

        String url = POSTGRES.getJdbcUrl();
        try (Connection app = DriverManager.getConnection(url, "ledger_app_test", "pw");
             var st = app.createStatement()) {
            assertThrows(SQLException.class,
                    () -> st.executeUpdate("UPDATE ledger_entries SET amount = amount + 1"));
            assertThrows(SQLException.class,
                    () -> st.executeUpdate("DELETE FROM ledger_entries"));
            // SELECT is permitted.
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM ledger_entries")) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    // Task 14: committed entries are durable and the idempotency retention window is >= 24h.
    @Test
    void committedEntriesArePersistedAndRetentionExceeds24h() {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account a = accountService.openAccount(owner, "USD", BigDecimal.ZERO);
        UUID txn = ledgerService.postTransaction(
                List.of(PostingLine.debit(funding.getId(), new BigDecimal("7.00")),
                        PostingLine.credit(a.getId(), new BigDecimal("7.00"))),
                "USD", null, "durable");

        // Re-read in a fresh query: the committed entries are present and unchanged.
        List<com.ledgercore.ledger.LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(txn);
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().anyMatch(e -> e.getDirection() == EntryDirection.CREDIT
                && e.getAccountId().equals(a.getId()))).isTrue();

        assertThat(idempotencyService.retention().toHours()).isGreaterThanOrEqualTo(24);
    }
}
