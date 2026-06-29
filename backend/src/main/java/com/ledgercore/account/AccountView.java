package com.ledgercore.account;

import com.ledgercore.common.money.Money;

import java.util.UUID;

/**
 * A read view of an account (Requirement 4.3): identifier, owner, currency, available
 * balance, and status.
 */
public record AccountView(UUID id, UUID ownerId, String currency, Money availableBalance,
                          AccountStatus status, Money overdraftLimit) {
}
