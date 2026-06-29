package com.ledgercore.authz;

import com.ledgercore.user.Role;

/**
 * The pure Role-Based Access Control decision matrix (Requirement 3).
 *
 * <ul>
 *   <li>CUSTOMER: may view/modify/transfer-from and read the ledger of accounts they own.</li>
 *   <li>TELLER: may read any account and ledger, and post transfers on behalf of customers.</li>
 *   <li>ADMIN: may read all accounts/ledgers, manage roles, and read the audit trail.</li>
 * </ul>
 */
public final class RbacPolicy {

    private RbacPolicy() {
    }

    /**
     * @param role          the requesting user's role
     * @param action        the action being attempted
     * @param ownsResource  whether the requester owns the targeted account (ignored for
     *                      global actions such as MANAGE_ROLES and VIEW_AUDIT)
     * @return {@code true} if the action is permitted.
     */
    public static boolean permits(Role role, AccessAction action, boolean ownsResource) {
        return switch (action) {
            case VIEW_ACCOUNT, VIEW_LEDGER -> switch (role) {
                case CUSTOMER -> ownsResource;
                case TELLER, ADMIN -> true;
            };
            case MODIFY_ACCOUNT -> switch (role) {
                case CUSTOMER -> ownsResource;
                case ADMIN -> true;
                case TELLER -> false;
            };
            case POST_TRANSFER -> switch (role) {
                case CUSTOMER -> ownsResource;   // may only transfer from an owned account
                case TELLER, ADMIN -> true;
            };
            case MANAGE_ROLES, VIEW_AUDIT -> role == Role.ADMIN;
        };
    }
}
