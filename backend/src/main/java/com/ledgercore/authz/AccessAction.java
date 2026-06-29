package com.ledgercore.authz;

/**
 * The authorizable actions in the system (design RBAC matrix, Requirement 3).
 */
public enum AccessAction {
    VIEW_ACCOUNT,
    MODIFY_ACCOUNT,
    POST_TRANSFER,
    VIEW_LEDGER,
    MANAGE_ROLES,
    VIEW_AUDIT
}
