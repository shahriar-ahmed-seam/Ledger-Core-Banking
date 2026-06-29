package com.ledgercore.authz;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Evaluates Role-Based Access Control and performs role management (Requirement 3).
 * Denials change no state (they are evaluated before any write).
 */
@Service
public class AuthzService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthzService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Authorizes an action against a possibly-owned resource, throwing on denial
     * (Requirements 3.2, 3.5).
     */
    public void authorize(AuthPrincipal principal, AccessAction action, boolean ownsResource) {
        if (!RbacPolicy.permits(principal.role(), action, ownsResource)) {
            throw DomainException.authorization("Insufficient permission for " + action + ".");
        }
    }

    /**
     * Changes a user's role (Requirements 3.6, 3.9). ADMIN-only; target must exist; the new
     * role must be valid. The change is recorded in the audit trail with the previous role,
     * new role, acting admin, and timestamp.
     */
    @Transactional
    public void changeUserRole(AuthPrincipal actor, UUID targetUserId, String newRoleName) {
        if (actor.role() != Role.ADMIN) {
            throw DomainException.authorization("Only ADMIN may change roles.");   // R3.4
        }
        Role newRole;
        try {
            newRole = Role.valueOf(newRoleName);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw DomainException.validation("Invalid role.", "role",
                    "must be one of CUSTOMER, TELLER, ADMIN");                       // R3.9
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> DomainException.validation("Target user does not exist.",
                        "userId", "no such user"));                                 // R3.9
        Role previous = target.getRole();
        target.setRole(newRole);
        userRepository.save(target);
        auditService.record(actor.userId(), AuditAction.ROLE_CHANGE, targetUserId.toString(), true,
                "role " + previous + " -> " + newRole + " by " + actor.userId());   // R3.6
    }
}
