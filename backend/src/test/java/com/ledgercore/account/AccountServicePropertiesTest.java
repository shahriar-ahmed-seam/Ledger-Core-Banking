package com.ledgercore.account;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.money.Currencies;
import com.ledgercore.common.money.Money;
import com.ledgercore.ledger.LedgerEntryRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Account_Service invariants using in-memory mocked repositories.
 */
class AccountServicePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Provide
    Arbitrary<String> supportedCurrency() {
        return Arbitraries.of(Currencies.supported().toArray(new String[0]));
    }

    private AccountService serviceWith(AccountRepository accounts, LedgerEntryRepository ledger) {
        return new AccountService(accounts, ledger, CLOCK);
    }

    // Feature: ledger-core-banking, Property 11: New accounts satisfy opening invariants
    @Property(tries = 200)
    void property11_newAccountsSatisfyOpeningInvariants(@ForAll("supportedCurrency") String ccy) {
        AccountRepository accounts = mock(AccountRepository.class);
        LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        AccountService service = serviceWith(accounts, ledger);

        UUID owner = UUID.randomUUID();
        Account a = service.openAccount(owner, ccy, BigDecimal.ZERO);

        assertThat(a.getId()).isNotNull();
        assertThat(a.getOwnerId()).isEqualTo(owner);
        assertThat(a.getCurrency()).isEqualTo(ccy);
        assertThat(a.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(a.getBalanceCache()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Feature: ledger-core-banking, Property 11 (unsupported currency creates no account)
    @Property(tries = 100)
    void property11_unsupportedCurrencyCreatesNoAccount(@ForAll String code) {
        if (Currencies.isSupported(code)) {
            return; // only exercise the unsupported branch here
        }
        AccountRepository accounts = mock(AccountRepository.class);
        LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
        AccountService service = serviceWith(accounts, ledger);

        assertThatThrownBy(() -> service.openAccount(UUID.randomUUID(), code, BigDecimal.ZERO))
                .isInstanceOf(DomainException.class);
        verify(accounts, never()).save(any());
    }

    // Feature: ledger-core-banking, Property 12: Account currency is immutable
    @Property(tries = 1)
    void property12_accountCurrencyIsImmutable() throws Exception {
        // There is no mutator for currency, and the column is non-updatable.
        boolean hasSetter = false;
        for (var m : Account.class.getMethods()) {
            if (m.getName().equalsIgnoreCase("setCurrency")) {
                hasSetter = true;
            }
        }
        assertThat(hasSetter).isFalse();
        var field = Account.class.getDeclaredField("currency");
        var column = field.getAnnotation(jakarta.persistence.Column.class);
        assertThat(column.updatable()).isFalse();
    }

    // Feature: ledger-core-banking, Property 13: Available balance is derived solely from posted entries
    @Property(tries = 300)
    void property13_availableBalanceDerivedFromEntries(@ForAll("supportedCurrency") String ccy,
                                                       @ForAll @IntRange(min = 0, max = 1_000_000) int creditMinor,
                                                       @ForAll @IntRange(min = 0, max = 1_000_000) int debitMinor) {
        int scale = Currencies.minorUnits(ccy);
        BigDecimal credits = BigDecimal.valueOf(creditMinor).movePointLeft(scale);
        BigDecimal debits = BigDecimal.valueOf(debitMinor).movePointLeft(scale);

        AccountRepository accounts = mock(AccountRepository.class);
        LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
        UUID id = UUID.randomUUID();
        when(ledger.sumCredits(id)).thenReturn(credits);
        when(ledger.sumDebits(id)).thenReturn(debits);
        AccountService service = serviceWith(accounts, ledger);

        Account account = new Account(id, UUID.randomUUID(), ccy, AccountStatus.ACTIVE,
                BigDecimal.ZERO.setScale(scale), BigDecimal.ZERO.setScale(scale), Instant.now(CLOCK));

        Money balance = service.getAvailableBalance(account);
        assertThat(balance).isEqualTo(Money.of(credits.subtract(debits), ccy));
    }

    // Feature: ledger-core-banking, Property 14: Account closure rules
    @Property(tries = 300)
    void property14_accountClosureRules(@ForAll("supportedCurrency") String ccy,
                                        @ForAll boolean alreadyClosed,
                                        @ForAll @IntRange(min = 0, max = 1000) int balanceMinor) {
        int scale = Currencies.minorUnits(ccy);
        BigDecimal credits = BigDecimal.valueOf(balanceMinor).movePointLeft(scale);

        AccountRepository accounts = mock(AccountRepository.class);
        LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
        UUID id = UUID.randomUUID();
        AccountStatus status = alreadyClosed ? AccountStatus.CLOSED : AccountStatus.ACTIVE;
        Account account = new Account(id, UUID.randomUUID(), ccy, status,
                BigDecimal.ZERO.setScale(scale), BigDecimal.ZERO.setScale(scale), Instant.now(CLOCK));
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.sumCredits(id)).thenReturn(credits);
        when(ledger.sumDebits(id)).thenReturn(BigDecimal.ZERO.setScale(scale));
        AccountService service = serviceWith(accounts, ledger);

        boolean shouldSucceed = !alreadyClosed && balanceMinor == 0;
        if (shouldSucceed) {
            service.closeAccount(id);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        } else {
            assertThatThrownBy(() -> service.closeAccount(id)).isInstanceOf(DomainException.class);
            assertThat(account.getStatus()).isEqualTo(status); // unchanged
        }
    }

    // Feature: ledger-core-banking, Property 15: Closed accounts reject money movement
    @Property(tries = 100)
    void property15_closedAccountsRejectMovement(@ForAll("supportedCurrency") String ccy,
                                                 @ForAll boolean closed) {
        AccountService service = serviceWith(mock(AccountRepository.class), mock(LedgerEntryRepository.class));
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID(), ccy,
                closed ? AccountStatus.CLOSED : AccountStatus.ACTIVE,
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(CLOCK));

        if (closed) {
            assertThatThrownBy(() -> service.ensureOpenForMovement(account))
                    .isInstanceOf(DomainException.class);
        } else {
            service.ensureOpenForMovement(account); // no throw
        }
    }
}
