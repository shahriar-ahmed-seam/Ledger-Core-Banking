package com.ledgercore.ledger;

import com.ledgercore.common.error.DomainException;
import com.ledgercore.common.money.Currencies;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pure validation of a candidate double-entry posting. Contains no I/O so the bookkeeping
 * invariants can be exercised directly by property tests.
 *
 * <p>Requirements: 5.1 (>=2 entries), 5.2/5.3 (balanced), 5.4/5.5 (single currency),
 * 5.6 (>=1 debit and >=1 credit).</p>
 */
public final class PostingValidator {

    private PostingValidator() {
    }

    /**
     * @return {@code true} if the lines form a well-formed, balanced posting for the currency.
     */
    public static boolean isValid(List<PostingLine> lines, String currency) {
        try {
            validate(lines, currency);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /**
     * Validates the posting, throwing a {@link DomainException} describing the first
     * violation found. Performs no persistence.
     */
    public static void validate(List<PostingLine> lines, String currency) {
        if (!Currencies.isSupported(currency)) {
            throw DomainException.validation("Unsupported currency.", "currency",
                    "not a supported ISO 4217 code");
        }
        if (lines == null || lines.size() < 2) {
            throw DomainException.validation("A transaction requires at least two entries.",
                    "entries", "must contain at least two ledger entries");
        }

        boolean hasDebit = false;
        boolean hasCredit = false;
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;

        for (PostingLine line : lines) {
            if (line.amount().signum() <= 0) {
                throw DomainException.validation("Ledger entry amounts must be positive.",
                        "amount", "must be greater than zero");
            }
            // Each line's amount must respect the currency's minor-unit precision.
            if (line.amount().scale() > Currencies.minorUnits(currency)) {
                throw DomainException.validation("Amount exceeds currency precision.",
                        "amount", "scale exceeds minor units for " + currency);
            }
            if (line.direction() == EntryDirection.DEBIT) {
                hasDebit = true;
                debitTotal = debitTotal.add(line.amount());
            } else {
                hasCredit = true;
                creditTotal = creditTotal.add(line.amount());
            }
        }

        if (!hasDebit || !hasCredit) {
            throw DomainException.ledgerImbalance(
                    "A transaction must contain at least one debit and at least one credit.");
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw DomainException.ledgerImbalance(
                    "Debit total " + debitTotal.toPlainString()
                            + " does not equal credit total " + creditTotal.toPlainString() + ".");
        }
    }

    /**
     * Computes the reversing lines for a set of original lines: each direction flipped, the
     * amount unchanged. The combined effect of the original and its reversal on every
     * account is exactly zero (Requirement 5.8).
     */
    public static List<PostingLine> reverse(List<PostingLine> original) {
        return original.stream()
                .map(l -> new PostingLine(l.accountId(), l.direction().flip(), l.amount()))
                .toList();
    }

    /**
     * Net signed effect on a single account across a set of lines
     * (CREDIT = +amount, DEBIT = -amount).
     */
    public static BigDecimal netEffect(List<PostingLine> lines, UUID accountId) {
        BigDecimal net = BigDecimal.ZERO;
        for (PostingLine l : lines) {
            if (l.accountId().equals(accountId)) {
                net = net.add(l.direction() == EntryDirection.CREDIT ? l.amount() : l.amount().negate());
            }
        }
        return net;
    }
}
