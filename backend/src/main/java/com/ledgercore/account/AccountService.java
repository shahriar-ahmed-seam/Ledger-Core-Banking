package com.ledgercore.account;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.money.Currencies;
import com.ledgercore.common.money.Money;
import com.ledgercore.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Account lifecycle and balance queries (Requirement 4).
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.clock = clock;
    }

    /**
     * Opens a new ACTIVE account with a zero balance and an immutable currency.
     * Requirements: 4.1, 4.2, 4.8.
     */
    @Transactional
    public Account openAccount(UUID ownerId, String currency, BigDecimal overdraftLimit) {
        if (!Currencies.isSupported(currency)) {
            throw DomainException.validation("Unsupported currency.", "currency",
                    "not a supported ISO 4217 code");
        }
        BigDecimal limit = overdraftLimit == null ? BigDecimal.ZERO : overdraftLimit;
        if (limit.signum() < 0) {
            throw DomainException.validation("Overdraft limit must be non-negative.",
                    "overdraftLimit", "must be >= 0");
        }
        Account account = new Account(
                UUID.randomUUID(), ownerId, currency, AccountStatus.ACTIVE,
                limit.setScale(Currencies.minorUnits(currency), java.math.RoundingMode.UNNECESSARY),
                BigDecimal.ZERO.setScale(Currencies.minorUnits(currency)), Instant.now(clock));
        return accountRepository.save(account);
    }

    /**
     * Returns a read view of an existing account, including its derived available balance.
     * Requirements: 4.3, 4.9.
     */
    @Transactional(readOnly = true)
    public AccountView viewAccount(UUID accountId) {
        Account account = requireAccount(accountId);
        Money balance = getAvailableBalance(account);
        return new AccountView(account.getId(), account.getOwnerId(), account.getCurrency(),
                balance, account.getStatus(),
                Money.of(account.getOverdraftLimit(), account.getCurrency()));
    }

    @Transactional(readOnly = true)
    public List<AccountView> listOwnedAccounts(UUID ownerId) {
        return accountRepository.findByOwnerId(ownerId).stream()
                .map(a -> new AccountView(a.getId(), a.getOwnerId(), a.getCurrency(),
                        getAvailableBalance(a), a.getStatus(),
                        Money.of(a.getOverdraftLimit(), a.getCurrency())))
                .toList();
    }

    /**
     * Closes an ACTIVE account with a zero balance.
     * Requirements: 4.5, 4.6, 4.9, 4.10.
     */
    @Transactional
    public void closeAccount(UUID accountId) {
        Account account = requireAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw DomainException.validation("Account is already closed.", "status",
                    "account already CLOSED");
        }
        Money balance = getAvailableBalance(account);
        if (!balance.isZero()) {
            throw DomainException.validation("Account balance must be zero to close.",
                    "balance", "non-zero balance");
        }
        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    /**
     * Computes available balance solely from posted ledger entries (Requirement 4.4):
     * {@code credits - debits}.
     */
    @Transactional(readOnly = true)
    public Money getAvailableBalance(UUID accountId) {
        return getAvailableBalance(requireAccount(accountId));
    }

    public Money getAvailableBalance(Account account) {
        BigDecimal credits = ledgerEntryRepository.sumCredits(account.getId());
        BigDecimal debits = ledgerEntryRepository.sumDebits(account.getId());
        return Money.of(credits.subtract(debits), account.getCurrency());
    }

    public Account requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account not found: " + accountId));
    }

    /**
     * Guards money movement against a CLOSED account (Requirement 4.7).
     *
     * @throws DomainException (ACCOUNT_CLOSED) if the account is not ACTIVE.
     */
    public void ensureOpenForMovement(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw DomainException.accountClosed(
                    "Account " + account.getId() + " is closed and cannot move money.");
        }
    }
}
