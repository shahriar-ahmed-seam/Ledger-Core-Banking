package com.ledgercore.auth;

import java.util.Locale;

/**
 * Email normalization and well-formedness (Requirements 1.2, 1.6).
 */
public final class EmailPolicy {

    private EmailPolicy() {
    }

    /** Case-insensitive normalization used for uniqueness comparison (Requirement 1.2). */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A well-formed email has exactly one "@" with a non-empty local part and a non-empty
     * domain part containing at least one "." (Requirement 1.6).
     */
    public static boolean isWellFormed(String email) {
        if (email == null) {
            return false;
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0 || at != e.lastIndexOf('@')) {
            return false;
        }
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        if (local.isEmpty() || domain.isEmpty()) {
            return false;
        }
        int dot = domain.indexOf('.');
        return dot > 0 && dot < domain.length() - 1;
    }
}
