package com.ledgercore.transfer;

import com.ledgercore.account.Account;
import com.ledgercore.account.AccountRepository;
import com.ledgercore.account.AccountService;
import com.ledgercore.account.AccountStatus;
import com.ledgercore.audit.AuditService;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.money.Money;
import com.ledgercore.idempotency.IdempotencyResult;
import com.ledgercore.idempotency.IdempotencyService;
import com.ledgercore.ledger.LedgerService;
import com.ledgercore.ledger.PostingLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Transfer_Service orchestration using mocked collaborators.
 * Concurrency/serializability (Property 23) is covered by the Testcontainers integration test.
 */
class TransferServicePropertiesTest {

    private static final String CCY = "USD";

    private record Fixture(TransferService service, LedgerService ledger,
                           AccountRepository accounts) {
    }

    private Fixture newFixture(Account source, Account dest, Money sourceBalance,
                               IdempotencyResult idemOutcome) {
        AccountRepository accounts = mock(AccountRepository.class);
        AccountService accountService = mock(AccountService.class);
        LedgerService ledger = mock(LedgerService.class);
        IdempotencyService idem = mock(IdempotencyService.class);
        AuditService audit = mock(AuditService.class);
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);

        if (source != null) {
            when(accounts.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        }
        if (dest != null) {
            when(accounts.findByIdForUpdate(dest.getId())).thenReturn(Optional.of(dest));
        }
        when(idem.begin(anyString(), anyString())).thenReturn(idemOutcome);
        if (source != null) {
            when(accountService.getAvailableBalance(source)).thenReturn(sourceBalance);
        }
        if (dest != null) {
            when(accountService.getAvailableBalance(dest)).thenReturn(Money.zero(CCY));
        }
        // ensureOpenForMovement uses the real behavior on a mock — stub to no-op / throw based on status.
        org.mockito.Mockito.doAnswer(inv -> {
            Account a = inv.getArgument(0);
            if (a.getStatus() != AccountStatus.ACTIVE) {
                throw DomainException.accountClosed("closed");
            }
            return null;
        }).when(accountService).ensureOpenForMovement(any(Account.class));
        when(ledger.postTransaction(any(), anyString(), any(), anyString()))
                .thenReturn(UUID.randomUUID());

        TransferService service = new TransferService(accounts, accountService, ledger, idem, audit,
                Duration.ofSeconds(5));
        ReflectionTestUtils.setField(service, "entityManager", em);
        return new Fixture(service, ledger, accounts);
    }

    private Account account(UUID id, String ccy, BigDecimal overdraft) {
        return new Account(id, UUID.randomUUID(), ccy, AccountStatus.ACTIVE, overdraft,
                BigDecimal.ZERO, Instant.parse("2025-01-01T00:00:00Z"));
    }

    // Feature: ledger-core-banking, Property 20: A valid transfer posts a single balanced debit/credit and conserves value
    @Property(tries = 200)
    void property20_validTransferPostsBalancedDebitCredit(@ForAll @IntRange(min = 1, max = 100000) int amtMinor,
                                                          @ForAll @IntRange(min = 0, max = 100000) int extraBalMinor) {
        UUID srcId = new UUID(0, 1);
        UUID dstId = new UUID(0, 2);
        Account src = account(srcId, CCY, BigDecimal.ZERO);
        Account dst = account(dstId, CCY, BigDecimal.ZERO);
        BigDecimal amount = BigDecimal.valueOf(amtMinor, 2);
        // Ensure sufficient funds: balance >= amount.
        Money balance = Money.of(amount.add(BigDecimal.valueOf(extraBalMinor, 2)), CCY);

        Fixture f = newFixture(src, dst, balance, IdempotencyResult.begun());
        TransferResult result = f.service().transfer(new TransferCommand(
                UUID.randomUUID(), "key-" + amtMinor, srcId, dstId, amount.toPlainString(), CCY));

        assertThat(result.transactionId()).isNotNull();
        assertThat(result.replayed()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostingLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(f.ledger()).postTransaction(captor.capture(), anyString(), any(), anyString());
        List<PostingLine> lines = captor.getValue();
        // Exactly one debit on source and one credit on destination, equal amounts.
        assertThat(lines).hasSize(2);
        PostingLine debit = lines.stream().filter(l -> l.direction().name().equals("DEBIT")).findFirst().orElseThrow();
        PostingLine credit = lines.stream().filter(l -> l.direction().name().equals("CREDIT")).findFirst().orElseThrow();
        assertThat(debit.accountId()).isEqualTo(srcId);
        assertThat(credit.accountId()).isEqualTo(dstId);
        assertThat(debit.amount()).isEqualByComparingTo(amount);
        assertThat(credit.amount()).isEqualByComparingTo(amount);
        // Value conserved: debit + credit net to zero.
        assertThat(debit.amount().subtract(credit.amount()).signum()).isZero();
    }

    // Feature: ledger-core-banking, Property 21: Invalid transfers are rejected and side-effect free
    @Property(tries = 200)
    void property21_invalidTransfersRejectedSideEffectFree(@ForAll @IntRange(min = -100, max = 0) int nonPositive,
                                                           @ForAll boolean sameAccount) {
        UUID srcId = new UUID(0, 1);
        UUID dstId = sameAccount ? srcId : new UUID(0, 2);
        Account src = account(srcId, CCY, BigDecimal.ZERO);
        Account dst = account(dstId, CCY, BigDecimal.ZERO);

        Fixture f = newFixture(src, dst, Money.of(BigDecimal.valueOf(1000), CCY),
                IdempotencyResult.begun());

        String amount = BigDecimal.valueOf(nonPositive, 2).toPlainString();
        assertThatThrownBy(() -> f.service().transfer(new TransferCommand(
                UUID.randomUUID(), "k", srcId, dstId, amount, CCY)))
                .isInstanceOf(DomainException.class);

        // No posting occurred (side-effect free rejection, R6.8).
        verify(f.ledger(), never()).postTransaction(any(), anyString(), any(), anyString());
    }

    // Feature: ledger-core-banking, Property 22 (service-level): insufficient funds rejected without posting
    @Property(tries = 200)
    void property22_insufficientFundsRejectedWithoutPosting(@ForAll @IntRange(min = 1, max = 100000) int amtMinor,
                                                            @ForAll @IntRange(min = 1, max = 100000) int shortfallMinor) {
        UUID srcId = new UUID(0, 1);
        UUID dstId = new UUID(0, 2);
        Account src = account(srcId, CCY, BigDecimal.ZERO);
        Account dst = account(dstId, CCY, BigDecimal.ZERO);
        BigDecimal amount = BigDecimal.valueOf(amtMinor, 2);
        // Balance strictly less than amount, with zero overdraft -> must be rejected.
        Money balance = Money.of(amount.subtract(BigDecimal.valueOf(shortfallMinor, 2)), CCY);

        Fixture f = newFixture(src, dst, balance, IdempotencyResult.begun());
        assertThatThrownBy(() -> f.service().transfer(new TransferCommand(
                UUID.randomUUID(), "k2", srcId, dstId, amount.toPlainString(), CCY)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code())
                        .isEqualTo(com.ledgercore.common.error.ErrorCode.INSUFFICIENT_FUNDS));
        verify(f.ledger(), never()).postTransaction(any(), anyString(), any(), anyString());
    }
}
