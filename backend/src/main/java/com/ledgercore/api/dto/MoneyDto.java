package com.ledgercore.api.dto;

import com.ledgercore.common.money.Money;

/**
 * API representation of money: a decimal string plus an explicit ISO 4217 currency
 * (Requirement 12.2).
 */
public record MoneyDto(String amount, String currency) {

    public static MoneyDto from(Money money) {
        return new MoneyDto(money.toDecimalString(), money.currency());
    }
}
