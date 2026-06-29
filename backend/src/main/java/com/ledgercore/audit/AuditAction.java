package com.ledgercore.audit;

/**
 * The set of sensitive actions recorded in the audit trail (Requirement 11.1).
 */
public enum AuditAction {
    AUTH_LOGIN,
    AUTH_REFRESH,
    AUTH_LOGOUT,
    ACCOUNT_OPEN,
    ACCOUNT_CLOSE,
    TRANSFER,
    ROLE_CHANGE,
    REVERSAL
}
