package com.ledgercore.authz;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.common.error.DomainException;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for the RBAC matrix and role management.
 */
class AuthzPropertiesTest {

    @Provide
    Arbitrary<Role> role() {
        return Arbitraries.of(Role.class);
    }

    @Provide
    Arbitrary<AccessAction> action() {
        return Arbitraries.of(AccessAction.class);
    }

    // Feature: ledger-core-banking, Property 9: RBAC decisions match the role/ownership matrix and denials are side-effect free
    @Property(tries = 500)
    void property9_rbacMatchesMatrix(@ForAll("role") Role role,
                                     @ForAll("action") AccessAction action,
                                     @ForAll boolean owns) {
        boolean permitted = RbacPolicy.permits(role, action, owns);

        // Independent oracle of the documented matrix.
        boolean expected = switch (action) {
            case VIEW_ACCOUNT, VIEW_LEDGER -> role != Role.CUSTOMER || owns;
            case MODIFY_ACCOUNT -> role == Role.ADMIN || (role == Role.CUSTOMER && owns);
            case POST_TRANSFER -> role != Role.CUSTOMER || owns;
            case MANAGE_ROLES, VIEW_AUDIT -> role == Role.ADMIN;
        };
        assertThat(permitted).isEqualTo(expected);

        // Denials are side-effect free: authorize() touches no repository, only throws.
        UserRepository users = mock(UserRepository.class);
        AuthzService service = new AuthzService(users, mock(AuditService.class));
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), role);
        if (permitted) {
            service.authorize(principal, action, owns); // no throw
        } else {
            assertThatThrownBy(() -> service.authorize(principal, action, owns))
                    .isInstanceOf(DomainException.class);
        }
        verify(users, never()).save(any());
    }

    // Feature: ledger-core-banking, Property 10: Role changes are valid-only and audited
    @Property(tries = 300)
    void property10_roleChangesValidOnlyAndAudited(@ForAll("role") Role actorRole,
                                                   @ForAll("role") Role newRole,
                                                   @ForAll boolean targetExists,
                                                   @ForAll boolean validRoleName) {
        UserRepository users = mock(UserRepository.class);
        AuditService audit = mock(AuditService.class);
        AuthzService service = new AuthzService(users, audit);

        AuthPrincipal actor = new AuthPrincipal(UUID.randomUUID(), actorRole);
        UUID targetId = UUID.randomUUID();
        User target = new User(targetId, "t@example.com", "h", Role.CUSTOMER, Instant.now());
        when(users.findById(targetId)).thenReturn(targetExists ? Optional.of(target) : Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String roleName = validRoleName ? newRole.name() : "WIZARD";
        boolean shouldSucceed = actorRole == Role.ADMIN && validRoleName && targetExists;

        if (shouldSucceed) {
            service.changeUserRole(actor, targetId, roleName);
            assertThat(target.getRole()).isEqualTo(newRole);
            verify(audit).record(eq(actor.userId()), eq(AuditAction.ROLE_CHANGE),
                    eq(targetId.toString()), eq(true), anyString());
        } else {
            assertThatThrownBy(() -> service.changeUserRole(actor, targetId, roleName))
                    .isInstanceOf(DomainException.class);
            // On any failure the target role is unchanged and no audit success is recorded.
            assertThat(target.getRole()).isEqualTo(Role.CUSTOMER);
            verify(audit, never()).record(any(), eq(AuditAction.ROLE_CHANGE), anyString(),
                    eq(true), anyString());
        }
    }
}
