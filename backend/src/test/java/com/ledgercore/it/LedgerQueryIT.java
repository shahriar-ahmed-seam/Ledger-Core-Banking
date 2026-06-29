package com.ledgercore.it;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountService;
import com.ledgercore.ledger.LedgerEntry;
import com.ledgercore.ledger.LedgerService;
import com.ledgercore.ledger.Page;
import com.ledgercore.ledger.PostingLine;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ledger query ordering, date filtering, and lossless pagination.
 */
class LedgerQueryIT extends AbstractPostgresIT {

    @Autowired
    LedgerService ledgerService;
    @Autowired
    AccountService accountService;
    @Autowired
    UserRepository userRepository;

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, id + "@example.com", "x", Role.CUSTOMER, Instant.now()));
        return id;
    }

    // Feature: ledger-core-banking, Property 29: Ledger queries are deterministically ordered
    // Feature: ledger-core-banking, Property 32: Pagination is bounded and lossless
    @Test
    void entriesAreOrderedAndPaginationIsLossless() {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account target = accountService.openAccount(owner, "USD", BigDecimal.ZERO);

        int n = 37;
        for (int i = 0; i < n; i++) {
            ledgerService.postTransaction(
                    List.of(PostingLine.debit(funding.getId(), new BigDecimal("1.00")),
                            PostingLine.credit(target.getId(), new BigDecimal("1.00"))),
                    "USD", null, "p" + i);
        }

        // Page through with size 10; concatenation reproduces the full ordered set exactly once.
        List<Long> seen = new ArrayList<>();
        Set<Long> unique = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            Page<LedgerEntry> page = ledgerService.listEntries(target.getId(), null, null, cursor, 10);
            assertThat(page.items().size()).isLessThanOrEqualTo(10); // bounded
            Instant prevTs = Instant.EPOCH;
            long prevId = -1;
            for (LedgerEntry e : page.items()) {
                // Deterministic ascending order by (postedAt, id).
                boolean ordered = e.getPostedAt().isAfter(prevTs)
                        || (e.getPostedAt().equals(prevTs) && e.getId() > prevId);
                assertThat(ordered).isTrue();
                prevTs = e.getPostedAt();
                prevId = e.getId();
                seen.add(e.getId());
                unique.add(e.getId());
            }
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null && pages < 100);

        assertThat(seen).hasSize(n);          // no omissions
        assertThat(unique).hasSize(n);        // no duplicates
    }

    // Feature: ledger-core-banking, Property 30: Date-range filtering is exact
    @Test
    void dateRangeFilteringIsInclusiveAndExact() {
        UUID owner = newUser();
        Account funding = accountService.openAccount(owner, "USD", new BigDecimal("100000000"));
        Account target = accountService.openAccount(owner, "USD", BigDecimal.ZERO);

        ledgerService.postTransaction(
                List.of(PostingLine.debit(funding.getId(), new BigDecimal("1.00")),
                        PostingLine.credit(target.getId(), new BigDecimal("1.00"))),
                "USD", null, "only");

        Page<LedgerEntry> all = ledgerService.listEntries(target.getId(), null, null, null, 50);
        Instant ts = all.items().get(0).getPostedAt();

        // Inclusive range around the single entry returns it.
        assertThat(ledgerService.listEntries(target.getId(), ts.minusSeconds(1), ts.plusSeconds(1), null, 50)
                .items()).hasSize(1);
        // A range entirely before the entry returns nothing.
        assertThat(ledgerService.listEntries(target.getId(), ts.minusSeconds(10), ts.minusSeconds(5), null, 50)
                .items()).isEmpty();
    }
}
