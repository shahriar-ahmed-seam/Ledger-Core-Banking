package com.ledgercore.api.dto;

import com.ledgercore.account.AccountView;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request/response DTOs for account endpoints (Requirement 4).
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(@NotBlank String currency, String overdraftLimit) {
    }

    public record AccountResponse(UUID id, UUID ownerId, String currency,
                                  MoneyDto availableBalance, String status,
                                  MoneyDto overdraftLimit) {
        public static AccountResponse from(AccountView v) {
            return new AccountResponse(v.id(), v.ownerId(), v.currency(),
                    MoneyDto.from(v.availableBalance()), v.status().name(),
                    MoneyDto.from(v.overdraftLimit()));
        }
    }
}
