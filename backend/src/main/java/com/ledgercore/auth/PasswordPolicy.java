package com.ledgercore.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * Password policy: 12-128 characters, at least one letter and at least one digit
 * (Requirement 1.5).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * @return the list of unmet policy criteria; empty if the password is compliant.
     */
    public static List<String> unmetCriteria(String password) {
        List<String> unmet = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            unmet.add("must be at least " + MIN_LENGTH + " characters");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            unmet.add("must be at most " + MAX_LENGTH + " characters");
        }
        boolean hasLetter = password != null && password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password != null && password.chars().anyMatch(Character::isDigit);
        if (!hasLetter) {
            unmet.add("must contain at least one letter");
        }
        if (!hasDigit) {
            unmet.add("must contain at least one digit");
        }
        return unmet;
    }

    public static boolean isValid(String password) {
        return unmetCriteria(password).isEmpty();
    }
}
